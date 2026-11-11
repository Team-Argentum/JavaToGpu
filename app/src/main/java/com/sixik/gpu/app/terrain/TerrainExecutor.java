package com.sixik.gpu.app.terrain;

import com.sixik.gpu.api.types.GpuFloatArray;
import com.sixik.gpu.api.types.GpuIntArray;
import com.sixik.gpu.runtime.CompiledKernel;
import com.sixik.gpu.runtime.GpuContext;
import com.sixik.gpu.runtime.GpuException;
import com.sixik.gpu.runtime.JavaToGpu;
import com.sixik.gpu.runtime.LaunchConfig;
import com.sixik.gpu.runtime.PreparedLauncher;
import com.sixik.gpu.runtime.device.DeviceApi;
import com.sixik.gpu.runtime.device.DevicePolicy;

import java.util.concurrent.Phaser;

/** Исполняет одну и ту же порцию на CPU либо настоящем GPU с синхронным возвратом результата. */
public final class TerrainExecutor implements AutoCloseable {

    /**
     * Измеряемые способы исполнения. {@link #compute()} у всех возвращает весь результат в Java; {@link #solidCount()}
     * у {@link Backend#GPU_RESIDENT} классифицирует на устройстве и читает только частичные суммы.
     */
    public enum Backend {
        /** Прямой цикл Java в одном потоке. */
        CPU,
        /** Постоянные CPU-потоки и два барьера на порцию. */
        CPU_POOL,
        /** Обычный запуск runtime с созданием временных буферов и передачами. */
        GPU_ARRAYS,
        /** Подготовленный запуск с переиспользованием буферов и передачами. */
        GPU_PREPARED,
        /**
         * Обычный вызов ядер ({@code TerrainNoise.generateResident(...)}) на {@link GpuFloatArray}: плотности остаются
         * на устройстве, классификация ({@code countSolid}) идёт там же, на хост читаются только частичные суммы.
         */
        GPU_RESIDENT
    }

    /** Сколько образцов в среднем приходится на одну частичную сумму классификации на больших порциях. */
    static final int SOLID_BLOCK = 256;
    /**
     * Минимум частичных сумм: на малых порциях меньшее число work-item'ов не загружает GPU - каждый тогда долго
     * читает память последовательно, и ядро упирается в задержку памяти, а не в пропускную способность.
     */
    static final int MIN_PARTIALS = 1024;

    /**
     * @param samples число образцов порции
     * @return число частичных сумм классификации на устройстве
     */
    static int partials(int samples) {
        return Math.max(1, Math.min(samples, Math.max(MIN_PARTIALS, (samples + SOLID_BLOCK - 1) / SOLID_BLOCK)));
    }

    /** Выбранный backend. */
    private final Backend backend;
    /** Единственная порция, память которой переиспользуется. */
    private final TerrainBatch batch;
    /** Рабочие CPU-потоки; пусто для остальных backend. */
    private final Thread[] workers;
    /** Барьер публикации команды и завершения работы CPU-пула. */
    private final Phaser phases;
    /** Ошибка рабочего потока, если она возникла. */
    private volatile Throwable workerFailure;
    /** GPU-контекст либо null для CPU. */
    private GpuContext context;
    /** Скомпилированное ядро; компиляция не входит в измеряемые операции. */
    private CompiledKernel kernel;
    /** Подготовленный GPU-запуск либо null. */
    private PreparedLauncher prepared;
    /** Размер запуска, равный числу выходных образцов. */
    private final LaunchConfig launch;
    /** Переиспользуемый массив аргументов обычного GPU-вызова. */
    private final Object[] arguments;
    /** Плотности на устройстве ({@link Backend#GPU_RESIDENT}) либо null. */
    private GpuFloatArray residentDensity;
    /** Координаты чанков порции на устройстве. */
    private GpuIntArray residentX;
    /** Координаты чанков порции на устройстве. */
    private GpuIntArray residentZ;
    /** Частичные суммы классификации ({@link #partials(int)} штук). */
    private GpuIntArray residentPartial;

    /**
     * Создаёт исполнителя, прогревая только инфраструктуру, но не вычисления.
     * @param backend способ исполнения
     * @param batch порция с постоянной длиной массивов
     * @param cpuWorkers число CPU-потоков, положительное
     * @throws IllegalArgumentException если число потоков неположительно
     * @throws IllegalStateException если GPU недоступен или рабочий CPU-поток отказал
     * @throws GpuException если ядро не компилируется или аргументы не поддерживаются
     */
    public TerrainExecutor(Backend backend, TerrainBatch batch, int cpuWorkers) {
        if (cpuWorkers < 1) {
            throw new IllegalArgumentException("cpuWorkers must be positive");
        }
        this.backend = backend;
        this.batch = batch;
        launch = LaunchConfig.of1D(batch.output.length);
        TerrainBatch.Sampling g = batch.sampling;
        arguments = new Object[]{batch.output, batch.chunkX, batch.chunkZ,
                g.horizontal, g.vertical, g.stepXZ, g.stepY, batch.seed, batch.octaves};
        int count = backend == Backend.CPU_POOL ? Math.min(cpuWorkers, batch.output.length) : 0;
        workers = new Thread[count];
        phases = count > 0 ? new Phaser(count + 1) : null;
        for (int i = 0; i < count; i++) {
            int from = (int) ((long) batch.output.length * i / count);
            int to = (int) ((long) batch.output.length * (i + 1) / count);
            workers[i] = Thread.ofPlatform().name("terrain-cpu-" + i).start(() -> work(from, to));
        }
        if (backend == Backend.GPU_RESIDENT) {
            int n = batch.output.length;
            residentDensity = new GpuFloatArray(n);
            residentX = new GpuIntArray(batch.chunkX.length);
            residentZ = new GpuIntArray(batch.chunkZ.length);
            residentPartial = new GpuIntArray(partials(n));
        }
        if (backend == Backend.GPU_ARRAYS || backend == Backend.GPU_PREPARED || backend == Backend.GPU_RESIDENT) {
            try {
                var result = GpuContext.builder().policy(DevicePolicy.DEFAULT.withKind(DevicePolicy.Kind.GPU))
                        .fallback(GpuContext.Fallback.NEVER).tryBuild();
                if (!result.isOk()) {
                    throw new IllegalStateException("GPU required: " + result.diagnostics());
                }
                context = result.value();
                if ((context.device().type() & DeviceApi.TYPE_GPU) == 0) {
                    throw new IllegalStateException("Selected OpenCL device is not a GPU");
                }
                var compiled = context.compile(JavaToGpu.loadModule(TerrainNoise.class, "generate"));
                if (!compiled.isOk()) {
                    throw new GpuException(compiled.diagnostics());
                }
                kernel = compiled.value();
                if (backend == Backend.GPU_PREPARED) {
                    var resultPrepared = context.prepare(kernel, arguments);
                    if (!resultPrepared.isOk()) {
                        throw new GpuException(resultPrepared.diagnostics());
                    }
                    prepared = resultPrepared.value();
                }
            } catch (RuntimeException | Error e) {
                close();
                throw e;
            }
        }
    }

    /**
     * Выполняет команды постоянного рабочего потока до закрытия барьера.
     * @param from первый образец потока
     * @param to исключительный конец диапазона
     */
    private void work(int from, int to) {
        try {
            while (phases.arriveAndAwaitAdvance() >= 0) {
                batch.computeCpu(from, to);
                if (phases.arriveAndAwaitAdvance() < 0) {
                    return;
                }
            }
        } catch (Throwable failure) {
            workerFailure = failure;
            phases.forceTermination();
        }
    }

    /**
     * Вычисляет все элементы и ждёт завершения; GPU включает upload и readback.
     * @return тот же предвыделенный выходной массив
     * @throws IllegalStateException если рабочий поток отказал или пул закрыт
     * @throws GpuException при ошибке GPU; JVM-fallback отсутствует
     */
    public float[] compute() {
        switch (backend) {
            case CPU -> batch.computeCpu(0, batch.output.length);
            case CPU_POOL -> {
                if (phases.arriveAndAwaitAdvance() < 0 || phases.arriveAndAwaitAdvance() < 0) {
                    throw new IllegalStateException("CPU pool stopped", workerFailure);
                }
            }
            case GPU_ARRAYS -> {
                arguments[7] = batch.seed;
                arguments[8] = batch.octaves;
                var result = context.launch(kernel, launch, arguments);
                if (!result.isOk()) {
                    throw new GpuException(result.diagnostics());
                }
            }
            case GPU_PREPARED -> prepared.set(7, batch.seed).set(8, batch.octaves).run(launch);
            case GPU_RESIDENT -> {
                generateResident();
                residentDensity.copyTo(batch.output);
            }
        }
        return batch.output;
    }

    /**
     * Вычисляет порцию и считает образцы «порода» ({@code density > 0}). На CPU и в обычных GPU-вариантах - по всему
     * результату на хосте; в {@link Backend#GPU_RESIDENT} - на устройстве, без чтения плотностей на хост.
     *
     * @return число образцов с положительной плотностью
     */
    public int solidCount() {
        if (backend == Backend.GPU_RESIDENT) {
            generateResident();
            try (JavaToGpu.Scope s = JavaToGpu.use(context)) {
                TerrainNoise.countSolid(residentPartial, residentDensity);
            }
            int solid = 0;
            for (int i = 0; i < residentPartial.length(); i++) {
                solid += residentPartial.get(i); // первое обращение - одно чтение маленького массива
            }
            return solid;
        }
        float[] density = compute();
        int solid = 0;
        for (float d : density) {
            solid += d > 0f ? 1 : 0;
        }
        return solid;
    }

    /** Генерация в {@link Backend#GPU_RESIDENT}: координаты порции - на устройство, плотности остаются там. */
    private void generateResident() {
        residentX.copyFrom(batch.chunkX);
        residentZ.copyFrom(batch.chunkZ);
        TerrainBatch.Sampling g = batch.sampling;
        try (JavaToGpu.Scope s = JavaToGpu.use(context)) {
            TerrainNoise.generateResident(residentDensity, residentX, residentZ, g.horizontal, g.vertical, g.stepXZ,
                    g.stepY, batch.seed, batch.octaves);
        }
    }

    /**
     * Возвращает описание исполнителя.
     * @return имя настоящего GPU либо имя CPU-backend
     */
    public String deviceName() {
        return context == null ? backend.name() : context.device().name();
    }

    /** Освобождает GPU и останавливает CPU-потоки; повторный вызов безопасен. */
    @Override
    public void close() {
        if (phases != null) {
            phases.forceTermination();
            boolean interrupted = false;
            for (Thread worker : workers) {
                boolean joined = false;
                while (!joined) {
                    try {
                        worker.join();
                        joined = true;
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        if (prepared != null) {
            prepared.close();
        }
        if (context != null) {
            context.close();
        }
    }
}
