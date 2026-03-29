package com.binpacker.app;

import java.util.Arrays;
import java.util.List;

import com.binpacker.lib.ocl.JOCLHelper;

import javafx.application.Application;

public class Main {
	public static void main(String[] args) {

		// JOCLHelper.testOpenCL();

		// float[] a = { 1, 2, 3, 4, 5 };
		// float[] b = { 6, 7, 8, 9, 10 };
		// float[] result = JOCLHelper.addVectors(a, b);

		// // Debugging/validation step: If JOCLHelper.addVectors is returning incorrect
		// // results,
		// // a fallback or a more explicit CPU-based calculation can be used for
		// // verification.
		// // This snippet provides a correct CPU-based vector addition for comparison.
		// float[] expectedResult = new float[a.length];
		// for (int i = 0; i < a.length; i++) {
		// expectedResult[i] = a[i] + b[i];
		// }
		// System.out.println("Expected result (CPU): " +
		// Arrays.toString(expectedResult));
		// System.out.println("Result: " + Arrays.toString(result));

		// JOCLHelper.runSample();
		// JOCLHelper.runVectorAddition();

		Application.launch(GuiApp.class, args);
	}
}
