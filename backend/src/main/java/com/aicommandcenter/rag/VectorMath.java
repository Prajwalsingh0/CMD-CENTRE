package com.aicommandcenter.rag;

/** Cosine similarity for the retrieval layer. Kept separate so it can be unit tested alone. */
public final class VectorMath {

    private VectorMath() {
    }

    /**
     * @return cosine similarity in {@code [-1, 1]}, or {@code 0} when the vectors are not
     *         comparable (null, empty or of different dimensionality)
     */
    public static double cosine(double[] left, double[] right) {
        if (left == null || right == null || left.length == 0 || left.length != right.length) {
            return 0.0;
        }
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
