package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionClassifierTest {
    private final GpuIrCommonSubexpressionClassifier classifier = new GpuIrCommonSubexpressionClassifier();

    @Test
    void classifiesLocalReuseAsRewriteReady() {
        GpuIrCommonSubexpression candidate = new GpuIrCommonSubexpression("binary(+,var(a),var(b))", 2, List.of("a", "b"));

        assertEquals(GpuIrCommonSubexpressionKind.LOCAL_REUSE, classifier.classify(candidate));
        assertTrue(classifier.isRewriteReady(candidate));
    }

    @Test
    void classifiesAssociativeBinaryReuseAsRewriteReady() {
        GpuIrCommonSubexpression candidate = new GpuIrCommonSubexpression("binary_assoc(&,var(a),var(b),var(c))", 2, List.of("a", "b"));

        assertEquals(GpuIrCommonSubexpressionKind.LOCAL_REUSE, classifier.classify(candidate));
        assertTrue(classifier.isRewriteReady(candidate));
    }

    @Test
    void classifiesHelperAndIntrinsicReuseSeparately() {
        GpuIrCommonSubexpression helper = new GpuIrCommonSubexpression("helper(noise,int,var(x))", 2, List.of("a", "b"));
        GpuIrCommonSubexpression intrinsic = new GpuIrCommonSubexpression("intrinsic(native,tpl,int,receiver(null),var(x))", 2, List.of("c", "d"));

        assertEquals(GpuIrCommonSubexpressionKind.HELPER_REUSE, classifier.classify(helper));
        assertEquals(GpuIrCommonSubexpressionKind.INTRINSIC_REUSE, classifier.classify(intrinsic));
        assertFalse(classifier.isRewriteReady(helper));
        assertFalse(classifier.isRewriteReady(intrinsic));
    }

    @Test
    void classifiesLeafAndUnknownShapesAsUnsafeForRewrite() {
        GpuIrCommonSubexpression leaf = new GpuIrCommonSubexpression("var(x)", 2, List.of("a", "b"));
        GpuIrCommonSubexpression unknown = new GpuIrCommonSubexpression("custom(x)", 2, List.of("c", "d"));

        assertEquals(GpuIrCommonSubexpressionKind.UNSAFE_FOR_REWRITE, classifier.classify(leaf));
        assertEquals(GpuIrCommonSubexpressionKind.UNSAFE_FOR_REWRITE, classifier.classify(unknown));
        assertFalse(classifier.isRewriteReady(leaf));
        assertFalse(classifier.isRewriteReady(unknown));
    }
}
