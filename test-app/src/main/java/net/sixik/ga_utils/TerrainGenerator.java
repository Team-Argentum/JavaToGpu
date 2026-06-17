package net.sixik.ga_utils;

import net.sixik.ga_utils.javatogpu.api.FloatPtr;
import net.sixik.ga_utils.javatogpu.api.GPU;
import net.sixik.ga_utils.javatogpu.api.anotations.CCode;
import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntime;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClGpuRuntimeBackend;

public class TerrainGenerator {

    public static void main(String[] args) {
        // Размер тестового буфера (например, блок 16x16 для симуляции одного слоя чанка)
        int size = 256;

        float[] xCoords = new float[size];
        float[] zCoords = new float[size];
        float[] densityOut = new float[size];

        // Заполняем сетку координат X и Z
        for (int i = 0; i < size; i++) {
            xCoords[i] = (float) (i % 16); // Координата X (0...15)
            zCoords[i] = (float) (i / 16); // Координата Z (0...15)
        }

        // Инициализируем OpenCL бэкенд через try-with-resources для автоматического закрытия контекста
        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            GpuRuntime.setBackend(backend);

            System.out.println("Вызов ядра calculate_density напрямую (будет перехвачен рантаймом)...");

            // Вызываем @GPU метод. Твой таск rewriteGpuMethods подменит этот вызов на запуск OpenCL кернела
            TerrainGenerator.WorldGenKernel.calculate_density(xCoords, zCoords, densityOut);

            System.out.println("Вычисление карты плотности на GPU успешно завершено!");

            // Выведем первые несколько результатов для проверки корректности тригонометрии и аккумулятора
            System.out.println("\nПример расчетных данных (Первые 10 позиций):");
            for (int i = 0; i < 10; i++) {
                System.out.printf("Точка [X: %4.1f, Z: %4.1f] -> Результирующая плотность: %6.2f%n",
                        xCoords[i], zCoords[i], densityOut[i]);
            }

        } catch (RuntimeException exception) {
            System.out.println("Сбой при выполнении кода на GPU: " + exception.getMessage());
            exception.printStackTrace();
        } finally {
            // Возвращаем дефолтный бэкенд (например, CPU-фолбек), чтобы не ломать остальной рантайм приложения
            GpuRuntime.setBackend(GpuRuntime.defaultBackend());
        }
    }

    public static class WorldGenKernel {

        @net.sixik.ga_utils.javatogpu.api.anotations.GPU
        public static void calculate_density(
                @GPUGlobal float[] x_coords,
                @GPUGlobal float[] z_coords,
                @GPUGlobal float[] density_out
        ) {
            int id = GPU.get_global_id(0);
            float x = x_coords[id];
            float z = z_coords[id];

            FloatPtr heightAccumulator = new FloatPtr();
            heightAccumulator.value = 0.0f;

            // Симуляция сложения нескольких октав шума
            for (int octave = 1; octave <= 3; octave++) {
                TerrainUtils.add_noise_layer(heightAccumulator, x, z, octave);
            }

            float finalDensity = heightAccumulator.value;

            // Сглаживание и отсечение (clamp)
            finalDensity = finalDensity < 0.0f ? 0.0f : finalDensity;
            finalDensity = finalDensity > 255.0f ? 255.0f : finalDensity;

            density_out[id] = finalDensity;
        }
    }

    public static class TerrainUtils {

        @CCode(inline = true)
        public static void add_noise_layer(FloatPtr accumulator, float x, float z, int octave) {
            float freq = octave * 0.75f;
            float amplitude = 15.0f / octave;

            // Псевдо-генерация на основе синусов/косинусов для теста математики
            float noise = GPU.sin(x * freq) * GPU.cos(z * freq);

            accumulator.value += (noise * amplitude) + (octave * 1.5f);
        }
    }
}
