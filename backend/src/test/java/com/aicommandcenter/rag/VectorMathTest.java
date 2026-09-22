package com.aicommandcenter.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VectorMathTest {

    @Test
    @DisplayName("identical vectors score 1 and opposite vectors score -1")
    void identicalAndOpposite() {
        double[] vector = {0.6, 0.8};
        assertThat(VectorMath.cosine(vector, new double[]{0.6, 0.8})).isCloseTo(1.0,
                org.assertj.core.data.Offset.offset(1e-9));
        assertThat(VectorMath.cosine(vector, new double[]{-0.6, -0.8})).isCloseTo(-1.0,
                org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("orthogonal vectors score 0")
    void orthogonal() {
        assertThat(VectorMath.cosine(new double[]{1, 0}, new double[]{0, 1})).isZero();
    }

    @Test
    @DisplayName("shape mismatches and degenerate vectors score 0 instead of throwing")
    void incompatibleInputsAreSafe() {
        assertThat(VectorMath.cosine(new double[]{1, 2, 3}, new double[]{1, 2})).isZero();
        assertThat(VectorMath.cosine(new double[0], new double[0])).isZero();
        assertThat(VectorMath.cosine(null, new double[]{1})).isZero();
        assertThat(VectorMath.cosine(new double[]{1, 1}, null)).isZero();
    }

    @Test
    @DisplayName("a zero vector cannot produce NaN")
    void zeroVectorIsSafe() {
        double score = VectorMath.cosine(new double[]{0, 0, 0}, new double[]{1, 2, 3});
        assertThat(score).isZero();
        assertThat(Double.isNaN(score)).isFalse();
    }

    @Test
    @DisplayName("magnitude is ignored, only direction matters")
    void scaleInvariant() {
        double small = VectorMath.cosine(new double[]{1, 2, 3}, new double[]{2, 4, 6});
        double large = VectorMath.cosine(new double[]{100, 200, 300}, new double[]{2, 4, 6});
        assertThat(small).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(large).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    }
}
