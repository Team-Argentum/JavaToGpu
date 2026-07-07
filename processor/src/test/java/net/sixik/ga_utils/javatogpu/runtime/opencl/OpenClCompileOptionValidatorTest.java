package net.sixik.ga_utils.javatogpu.runtime.opencl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClCompileOptionValidatorTest {

    @Test
    void joinsSupportedOpenClBuildOptions() {
        String buildOptions = OpenClCompileOptionValidator.toBuildOptions(List.of(
                "-cl-fast-relaxed-math",
                "-cl-std=CL2.0",
                "-DWORKLOAD_SIZE=256",
                "-Igenerated/opencl"
        ));

        assertEquals("-cl-fast-relaxed-math -cl-std=CL2.0 -DWORKLOAD_SIZE=256 -Igenerated/opencl", buildOptions);
    }

    @Test
    void rejectsUnknownOpenClBuildOption() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> OpenClCompileOptionValidator.toBuildOptions(List.of("--cuda-fast-math"))
        );

        assertTrue(exception.getMessage().contains("Unsupported OpenCL compile option"));
        assertTrue(exception.getMessage().contains("--cuda-fast-math"));
    }

    @Test
    void rejectsWhitespaceSeparatedOpenClBuildOption() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> OpenClCompileOptionValidator.toBuildOptions(List.of("-DVALUE=1 -cl-opt-disable"))
        );

        assertTrue(exception.getMessage().contains("single command-line token"));
    }

    @Test
    void rejectsBlankOpenClBuildOption() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> OpenClCompileOptionValidator.toBuildOptions(List.of(" "))
        );

        assertTrue(exception.getMessage().contains("must not be blank"));
    }
}
