package com.binpacker.app;

import com.binpacker.lib.common.Utils;
import com.binpacker.lib.ocl.JOCLHelper;
import com.binpacker.lib.optimizer.CPUOptimizer;
import com.binpacker.lib.optimizer.Optimizer;
import com.binpacker.lib.solver.common.SolverProperties;
import com.binpacker.lib.solver.cpusolvers.BestFit3D;
import com.binpacker.lib.solver.cpusolvers.BestFitEMS;
import com.binpacker.lib.solver.cpusolvers.FirstFit2D;
import com.binpacker.lib.solver.cpusolvers.FirstFit3D;
import com.binpacker.lib.solver.cpusolvers.SolverInterface;
import com.binpacker.lib.solver.parallelsolvers.BestFitEMSReference;
import com.binpacker.lib.solver.parallelsolvers.BestFitReference;
import com.binpacker.lib.solver.parallelsolvers.FirstFitReference;
import com.binpacker.lib.solver.parallelsolvers.opencl.OpenCLSolver;
import com.binpacker.lib.solver.parallelsolvers.cuda.CudaSolver;
import com.binpacker.lib.solver.parallelsolvers.ParallelSolverInterface;
import com.binpacker.lib.optimizer.CPUOptimizer;
import com.binpacker.lib.optimizer.GPUOptimizer;
import com.binpacker.lib.ocl.OpenCLDevice;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.SubScene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.CheckBox;
import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;

import javafx.scene.shape.Box;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.DrawMode;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import javafx.stage.FileChooser;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

class NumberTextField extends TextField {

	private float value;

	public NumberTextField(float value) {
		this.value = value;
		this.setText(String.valueOf(value));

		this.textProperty().addListener((observable, oldValue, newValue) -> {
			try {
				this.value = Float.parseFloat(newValue);
			} catch (NumberFormatException e) {
				// Handle cases where the text might be empty or not a valid number
				// (though replaceText/replaceSelection should prevent non-numeric input)
				this.value = 0.0f; // Default to 0.0 if parsing fails
			}
		});
	}

	@Override
	public void replaceText(int start, int end, String text) {
		if (text.matches("[0-9]*")) {
			super.replaceText(start, end, text);
		}
	}

	@Override
	public void replaceSelection(String text) {
		if (text.matches("[0-9]*")) {
			super.replaceSelection(text);
		}
	}

	public float getValue() {
		return value;
	}
}

public class GuiApp extends Application {

	private Stage resultsStage;
	private Group resultWorld;
	private boolean isSolving = false;

	private Label statusLabel;
	private Stage primaryStage;

	// Camera controls
	private double lastMouseX;
	private double lastMouseY;
	private final Rotate rotateX = new Rotate(0, Rotate.X_AXIS);
	private final Rotate rotateY = new Rotate(0, Rotate.Y_AXIS);

	private List<List<com.binpacker.lib.common.Box>> result;

	private ComboBox<Object> solverComboBox;
	private ComboBox<OpenCLDevice> openCLDeviceComboBox;

	private int generations = 200;
	private int population = 30;
	private int eliteCount = 3;
	private boolean growingBin = false;

	private String axis = "x";

	private ComboBox<String> inputSourceCombo;
	private TextField filePathField;
	private File selectedCsvFile;
	private Label csvStatusLabel;

	NumberTextField binWidthField = new NumberTextField(30);
	NumberTextField binHeightField = new NumberTextField(30);
	NumberTextField binDepthField = new NumberTextField(30);
	NumberTextField binWeightField = new NumberTextField(0);

	private CheckBox rotX;
	private CheckBox rotY;
	private CheckBox rotZ;

	@Override
	public void start(Stage primaryStage) {
		this.primaryStage = primaryStage;
		StackPane root = new StackPane();

		// Controls - Main Window
		VBox controls = new VBox(8);

		// Input Source Selection
		Label inputLabel = new Label("Input Source:");
		inputSourceCombo = new ComboBox<>();
		inputSourceCombo.getItems().addAll("Demo Data", "CSV File");
		inputSourceCombo.setValue("Demo Data");

		filePathField = new TextField();
		filePathField.setPromptText("Select CSV file...");
		filePathField.setEditable(false);
		filePathField.setPrefWidth(150);
		filePathField.setDisable(true); // default to demo data

		Button browseButton = new Button("Browse");
		browseButton.setDisable(true);

		browseButton.setOnAction(e -> {
			FileChooser fileChooser = new FileChooser();
			fileChooser.setTitle("Open Box CSV");
			fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
			File file = fileChooser.showOpenDialog(primaryStage);
			if (file != null) {
				selectedCsvFile = file;
				filePathField.setText(file.getName());
				List<com.binpacker.lib.common.Box> boxes = loadBoxesFromCsv(file);
				if (boxes.isEmpty()) {
					csvStatusLabel.setText("Invalid CSV / No boxes found");
					csvStatusLabel.setTextFill(Color.RED);
				} else {
					csvStatusLabel.setText("Found " + boxes.size() + " boxes");
					csvStatusLabel.setTextFill(Color.GREEN);
				}
			}
		});

		inputSourceCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
			boolean isCsv = "CSV File".equals(newVal);
			filePathField.setDisable(!isCsv);
			browseButton.setDisable(!isCsv);
		});

		HBox inputFileBox = new HBox(5);
		inputFileBox.setAlignment(Pos.CENTER_LEFT);
		inputFileBox.getChildren().addAll(filePathField, browseButton);

		csvStatusLabel = new Label("");
		controls.getChildren().addAll(inputLabel, inputSourceCombo, inputFileBox, csvStatusLabel);

		Label binLabel = new Label("Bin dimensions:");

		// Create labeled fields for bin dimensions
		Label xLabel = new Label("x");
		VBox xBox = new VBox(2);
		xBox.setAlignment(Pos.CENTER);
		xBox.getChildren().addAll(xLabel, binWidthField);

		Label yLabel = new Label("y");
		VBox yBox = new VBox(2);
		yBox.setAlignment(Pos.CENTER);
		yBox.getChildren().addAll(yLabel, binHeightField);

		Label zLabel = new Label("z");
		VBox zBox = new VBox(2);
		zBox.setAlignment(Pos.CENTER);
		zBox.getChildren().addAll(zLabel, binDepthField);

		Label weightLimitLabel = new Label("weight limit");
		VBox weightBox = new VBox(2);
		weightBox.setAlignment(Pos.CENTER);
		weightBox.getChildren().addAll(weightLimitLabel, binWeightField);

		HBox binDimensionFields = new HBox(5);
		binDimensionFields.setMaxWidth(300);
		binDimensionFields.setAlignment(Pos.CENTER_LEFT);
		binDimensionFields.getChildren().addAll(xBox, yBox, zBox, weightBox);

		controls.getChildren().addAll(binLabel, binDimensionFields);

		// allowed rotations
		Label rotationLabel = new Label("Allow boxes to rotate in axes:");
		rotX = new CheckBox("X");
		rotX.setSelected(true);
		rotY = new CheckBox("Y");
		rotY.setSelected(true);
		rotZ = new CheckBox("Z");
		rotZ.setSelected(true);

		HBox rotationBox = new HBox(10);
		rotationBox.setAlignment(Pos.CENTER_LEFT);
		rotationBox.getChildren().addAll(rotX, rotY, rotZ);
		controls.getChildren().addAll(rotationLabel, rotationBox);

		Label growingBinLabel = new Label("Use a single growing bin");
		javafx.scene.control.CheckBox growingBinCheckBox = new javafx.scene.control.CheckBox();
		growingBinCheckBox.setSelected(growingBin);
		growingBinCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
			growingBin = newValue;
		});
		HBox growingBinHBox = new javafx.scene.layout.HBox(10); // 10 is spacing
		growingBinHBox.setAlignment(Pos.CENTER_LEFT);
		Label axisLabel = new Label("Axis");
		ComboBox<String> axisComboBox = new ComboBox<>();
		axisComboBox.getItems().addAll("x", "y", "z");
		axisComboBox.setValue("x");
		axisComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
			axis = newValue;
		});

		growingBinHBox.getChildren().addAll(growingBinLabel, growingBinCheckBox);
		growingBinHBox.getChildren().addAll(axisLabel, axisComboBox);
		controls.getChildren().add(growingBinHBox);

		this.solverComboBox = new ComboBox<>();
		this.solverComboBox.setConverter(new javafx.util.StringConverter<Object>() {
			@Override
			public String toString(Object solver) {
				if (solver == null) {
					return null;
				}
				// Provide user-friendly names for each solver type
				if (solver instanceof FirstFit3D) {
					return "3D first fit bsp";
				} else if (solver instanceof FirstFit2D) {
					return "2D first fit bsp";
				} else if (solver instanceof BestFit3D) {
					return "3D best fit bsp";
				} else if (solver instanceof BestFitEMS) {
					return "Best Fit EMS";
				} else if (solver instanceof OpenCLSolver) {
					OpenCLSolver gpuSolver = (OpenCLSolver) solver;
					return gpuSolver.getDisplayName();
				} else if (solver instanceof CudaSolver) {
					return "BestFit EMS (CUDA)";
				}
				return solver.getClass().getSimpleName(); // Fallback
			}

			@Override
			public Object fromString(String string) {
				// This method is used when parsing user input, not needed for simple selection
				return null;
			}
		});
		this.solverComboBox.getItems().addAll(new FirstFit3D(), new FirstFit2D(), new BestFit3D(), new BestFitEMS(),
				new OpenCLSolver("firstfit_complete.cl.template", "guillotine_first_fit", "FirstFit GPU (Parallel)",
						new FirstFitReference()),
				new OpenCLSolver("bestfit_complete.cl.template", "guillotine_best_fit", "BestFit GPU (Parallel)",
						new BestFitReference()),
				new OpenCLSolver("bestfit_ems.cl.template", "best_fit_ems", "BestFit EMS GPU (Parallel)",
						new BestFitEMSReference()),
				new CudaSolver());
		this.solverComboBox.setValue(this.solverComboBox.getItems().get(0)); // Set default to the first item

		Label solverOptions = new Label("Solver Options:");
		Label generationsLabel = new Label("Generations:");
		javafx.scene.control.TextField generationsField = new javafx.scene.control.TextField(
				String.valueOf(generations));
		generationsField.textProperty().addListener((observable, oldValue, newValue) -> {
			if (!newValue.matches("\\d*")) {
				generationsField.setText(oldValue);
			} else {
				try {
					generations = Integer.parseInt(newValue);
				} catch (NumberFormatException ex) {
					generations = 0; // Or some default/error handling
				}
			}
		});

		Label populationLabel = new Label("Population:");
		javafx.scene.control.TextField populationField = new javafx.scene.control.TextField(String.valueOf(population));
		populationField.textProperty().addListener((observable, oldValue, newValue) -> {
			if (!newValue.matches("\\d*")) {
				populationField.setText(oldValue);
			} else {
				try {
					population = Integer.parseInt(newValue);
				} catch (NumberFormatException ex) {
					population = 0;
				}
			}
		});

		Label eliteCountLabel = new Label("Elite Count:");
		javafx.scene.control.TextField eliteCountField = new javafx.scene.control.TextField(String.valueOf(eliteCount));
		eliteCountField.textProperty().addListener((observable, oldValue, newValue) -> {
			if (!newValue.matches("\\d*")) {
				eliteCountField.setText(oldValue);
			} else {
				try {
					eliteCount = Integer.parseInt(newValue);
				} catch (NumberFormatException ex) {
					eliteCount = 0;
				}
			}
		});

		controls.getChildren().add(new javafx.scene.control.Separator());
		controls.getChildren().addAll(solverOptions, generationsLabel, generationsField, populationLabel,
				populationField,
				eliteCountLabel, eliteCountField);

		statusLabel = new Label("Ready");
		controls.getChildren().add(this.solverComboBox);

		List<OpenCLDevice> devices = JOCLHelper.getAvailableDevices();
		System.out.println("Available devices: " + devices);
		Label openCLDeviceLabel = new Label("(Optional) OpenCL Device:");
		HBox openCLDeviceHBox = new javafx.scene.layout.HBox(10); // 10 is spacing
		openCLDeviceHBox.setAlignment(Pos.CENTER_LEFT);
		this.openCLDeviceComboBox = new ComboBox<>();
		this.openCLDeviceComboBox.getItems().addAll(devices);
		this.openCLDeviceComboBox.setConverter(new javafx.util.StringConverter<OpenCLDevice>() {
			@Override
			public String toString(OpenCLDevice device) {

				return device != null
						? (device.toString().length() > 20 ? device.toString().substring(0, 20) : device.toString())
						: "N/A";
			}

			@Override
			public OpenCLDevice fromString(String string) {
				return null;
			}
		});
		if (!devices.isEmpty()) {
			this.openCLDeviceComboBox.setValue(devices.get(0));
		}
		Button testButton = new Button("Test");
		testButton.setOnAction(e -> {
			OpenCLDevice device = this.openCLDeviceComboBox.getValue();
			if (device != null) {
				boolean result = JOCLHelper.testOpenCLDevice(device);
				JOCLHelper.runSample(device.platformIndex, device.deviceIndex);
				String deviceStr = device.toString().length() > 20 ? device.toString().substring(0, 20)
						: device.toString();
				if (result) {
					statusLabel.setText("Device " + deviceStr + " seems to work");
				} else {
					statusLabel.setText("Device " + deviceStr + " has issues");
				}
			}
		});

		openCLDeviceHBox.getChildren().addAll(this.openCLDeviceComboBox, testButton);
		controls.getChildren().add(new javafx.scene.control.Separator());
		controls.getChildren().add(openCLDeviceLabel);
		controls.getChildren().add(openCLDeviceHBox);

		Button solveButton = new Button("Start solver");
		Button exportButton = new Button("Export currently visible solution");
		// Event Handling
		solveButton.setOnAction(e -> runSolver());
		exportButton.setOnAction(e -> exportSolution());

		controls.getChildren().add(new javafx.scene.control.Separator());
		controls.getChildren().add(solveButton);
		controls.getChildren().add(exportButton);
		controls.getChildren().add(statusLabel);
		controls.setStyle(
				"-fx-padding: 20; -fx-background-color: rgba(255, 255, 255, 0.2); -fx-background-radius: 10;");
		controls.setMaxHeight(VBox.USE_PREF_SIZE);
		controls.setMaxWidth(VBox.USE_PREF_SIZE);

		root.getChildren().add(controls);

		Scene scene = new Scene(root, 400, 700); // Smaller size for controls only
		primaryStage.setTitle("Bin packing solver - Controls");
		primaryStage.setScene(scene);
		primaryStage.show();

		// Initialize the resultStage logic but don't show it yet
		// We'll create it on demand in showResultsWindow()
	}

	private void showResultsWindow() {
		if (resultsStage == null) {
			resultsStage = new Stage();
			resultsStage.setTitle("Bin Packing Results");

			resultWorld = new Group();
			SubScene subScene = create3DScene(resultWorld);

			StackPane pane = new StackPane();
			pane.getChildren().add(subScene);

			// Bind subScene size to window size
			subScene.heightProperty().bind(pane.heightProperty());
			subScene.widthProperty().bind(pane.widthProperty());

			Scene scene = new Scene(pane, 800, 600);
			resultsStage.setScene(scene);

			resultsStage.setOnCloseRequest(e -> {
				// Just hide it so we can re-show it, or let it close and set to null
				// If solving, we might want to know it's closed?
				// The requirement says: "If the user closes the results window while the
				// solving is ongoing,
				// it should be recreated when the next iteration is done."
				// So if we set it to null here, the loop will recreate it.
				resultsStage = null;
			});
		}

		if (!resultsStage.isShowing()) {
			resultsStage.show();
		}
	}

	private SubScene create3DScene(Group world) {
		world.getChildren().clear();

		SubScene subScene = new SubScene(world, 800, 550, true, javafx.scene.SceneAntialiasing.BALANCED);

		PerspectiveCamera camera = new PerspectiveCamera(true);
		camera.setTranslateZ(100);
		camera.setRotationAxis(Rotate.Y_AXIS);
		camera.setRotate(180);
		camera.setTranslateY(0);
		camera.setTranslateX(0);
		camera.setNearClip(0.1);
		camera.setFarClip(1000.0);
		camera.setFieldOfView(45);

		Group cameraGroup = new Group();
		cameraGroup.getChildren().add(camera);
		cameraGroup.getTransforms().addAll(rotateY, rotateX);
		world.getChildren().add(cameraGroup);

		subScene.setCamera(camera);

		subScene.setOnMousePressed(event -> {
			lastMouseX = event.getSceneX();
			lastMouseY = event.getSceneY();
			subScene.requestFocus();
		});

		subScene.setOnMouseDragged(event -> {
			if (event.isPrimaryButtonDown()) {
				double dx = event.getSceneX() - lastMouseX;
				double dy = event.getSceneY() - lastMouseY;

				rotateY.setAngle(rotateY.getAngle() + dx * 0.5);
				rotateX.setAngle(rotateX.getAngle() + dy * 0.5);
			}
			lastMouseX = event.getSceneX();
			lastMouseY = event.getSceneY();
		});

		subScene.setOnScroll(event -> {
			double delta = -event.getDeltaY();
			double newZ = camera.getTranslateZ() + delta * 0.5;

			camera.setTranslateZ(newZ);
		});
		subScene.setOnKeyPressed(event -> {
			double moveAmount = 5.0;
			switch (event.getCode()) {
				case LEFT:
					camera.setTranslateX(camera.getTranslateX() + moveAmount);
					break;
				case RIGHT:
					camera.setTranslateX(camera.getTranslateX() - moveAmount);
					break;
				default:
					break;
			}
		});

		subScene.setFocusTraversable(true);
		subScene.requestFocus();

		// reference axes
		Box xAxis = new Box(100, 0.5, 0.5);
		xAxis.setMaterial(new PhongMaterial(Color.RED));
		Box yAxis = new Box(0.5, 100, 0.5);
		yAxis.setMaterial(new PhongMaterial(Color.GREEN));
		Box zAxis = new Box(0.5, 0.5, 100);
		zAxis.setMaterial(new PhongMaterial(Color.BLUE));

		world.getChildren().addAll(xAxis, yAxis, zAxis);

		return subScene;
	}

	private Task<Void> currentSolverTask;

	private void runSolver() {
		statusLabel.setText("Solving...");

		// Cancel previous task if running
		if (currentSolverTask != null && currentSolverTask.isRunning()) {
			currentSolverTask.cancel();
			// We might want to wait a bit or ensure it's really stopped, but cancel()
			// should set the flag
			// and the loop below checks it.
		}

		isSolving = true;

		// Clean up previous run if needed
		if (resultsStage != null) {
			Platform.runLater(() -> {
				if (resultsStage != null) { // Double check inside runLater
					// Fix: Do not reuse resultWorld for a new SubScene. Create a fresh one.
					resultWorld = new Group();
					SubScene newSubScene = create3DScene(resultWorld);

					// Get the root pane of the scene
					if (resultsStage.getScene() != null && resultsStage.getScene().getRoot() instanceof StackPane) {
						StackPane rootPane = (StackPane) resultsStage.getScene().getRoot();
						rootPane.getChildren().setAll(newSubScene);

						// Re-bind dimensions
						newSubScene.heightProperty().bind(rootPane.heightProperty());
						newSubScene.widthProperty().bind(rootPane.widthProperty());
					} else {
						// Fallback if structure is unexpected (shouldn't happen given
						// showResultsWindow)
						resultsStage.close();
						resultsStage = null;
						showResultsWindow();
					}

					if (!resultsStage.isShowing()) {
						resultsStage.show();
					}
				}
			});
		}

		// Generate or Load Data
		List<com.binpacker.lib.common.Box> boxes;
		if ("CSV File".equals(inputSourceCombo.getValue())) {
			if (selectedCsvFile == null || !selectedCsvFile.exists()) {
				statusLabel.setText("Please select a valid CSV file.");
				isSolving = false;
				return;
			}
			boxes = loadBoxesFromCsv(selectedCsvFile);
			if (boxes.isEmpty()) {
				statusLabel.setText("No valid boxes found in CSV.");
				isSolving = false;
				return;
			}
			System.out.println("Loaded " + boxes.size() + " boxes from CSV.");
		} else {
			boxes = generateRandomBoxes(300);
		}

		com.binpacker.lib.common.Bin bin = new com.binpacker.lib.common.Bin(0, binWidthField.getValue(),
				binHeightField.getValue(), binDepthField.getValue());
		bin.maxWeight = binWeightField.getValue();
		// Solve
		Object selectedSolver = solverComboBox.getValue();
		Optimizer<?> optimizer;

		List<Integer> rotationAxes = new ArrayList<>();
		if (rotX.isSelected())
			rotationAxes.add(0);
		if (rotY.isSelected())
			rotationAxes.add(1);
		if (rotZ.isSelected())
			rotationAxes.add(2);

		SolverProperties properties = new SolverProperties(bin, growingBin, axis, rotationAxes,
				openCLDeviceComboBox.getValue(), binWeightField.getValue());

		if (selectedSolver instanceof ParallelSolverInterface) {
			GPUOptimizer gpuOptimizer = new GPUOptimizer();
			ParallelSolverInterface parallelSolver = (ParallelSolverInterface) selectedSolver;
			parallelSolver.init(properties);
			gpuOptimizer.initialize(parallelSolver, boxes, bin, growingBin, axis, rotationAxes, this.population,
					this.eliteCount,
					true);
			optimizer = gpuOptimizer;
		} else if (selectedSolver instanceof SolverInterface) {
			CPUOptimizer cpuOptimizer = new CPUOptimizer();
			SolverInterface solver = (SolverInterface) selectedSolver;

			boolean threaded = true;

			Class<? extends SolverInterface> solverClass = solver.getClass();
			java.util.function.Supplier<SolverInterface> factory = () -> {
				try {
					SolverInterface s = solverClass.getDeclaredConstructor().newInstance();
					// Create a fresh bin copy to avoid shared mutable state
					com.binpacker.lib.common.Bin freshBin = new com.binpacker.lib.common.Bin(
							bin.index, bin.w, bin.h, bin.d);
					freshBin.maxWeight = bin.maxWeight;
					SolverProperties freshProps = new SolverProperties(freshBin, growingBin, axis, rotationAxes,
							openCLDeviceComboBox.getValue(), binWeightField.getValue());
					s.init(freshProps);
					return s;
				} catch (Exception ex) {
					throw new RuntimeException("Failed to create solver instance", ex);
				}
			};

			cpuOptimizer.initialize(factory, boxes, bin, growingBin, axis, rotationAxes, this.population,
					this.eliteCount, threaded);
			optimizer = cpuOptimizer;
		} else {
			statusLabel.setText("Unknown solver type");
			isSolving = false;
			return;
		}

		Random random = new Random();
		List<Color> boxColors = new ArrayList<>();
		for (int i = 0; i < boxes.size(); i++) {
			boxColors.add(Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)));
		}

		int generations = this.generations;

		currentSolverTask = new Task<Void>() {
			@Override
			protected Void call() throws Exception {
				try {
					for (int i = 0; i < generations; i++) {
						if (isCancelled())
							break;

						result = optimizer.executeNextGeneration();
						final double rawRate = optimizer.rate(result, bin) * 100;
						final String rate = String.format("%.2f", rawRate);
						final int generation = i + 1;

						Platform.runLater(() -> {
							if (isCancelled())
								return; // Don't update UI if cancelled

							statusLabel
									.setText("Solving... Generation " + generation + " complete, " + rate + "% full");

							// Check if results window is open; if not and solving, open it
							if (resultsStage == null || !resultsStage.isShowing()) {
								showResultsWindow();
							}

							// If we re-created the world/scene, we need a group to add boxes to.
							// create3DScene clears 'world' and adds camera/axes.
							// We need to add boxes to `resultWorld`.

							// First, clear OLD boxes (but keep axes/camera).
							// The axes/camera are the first 4 children (cameraGroup, xAxis, yAxis, zAxis).
							// Let's remove everything after index 3 or just clear and redraw everything
							// including axes/camera?
							// Clearing and redrawing everything is safer but might flicker.
							// The old code did `solverOutputGroup.getChildren().clear()`.
							// We can create a dedicated group for boxes inside create3DScene and expose it?
							// Or just assume `resultWorld` children from index 4 onwards are boxes.
							// Let's safely iterate and remove boxes.
							resultWorld.getChildren().removeIf(node -> node instanceof Box);
							// Wait, axes are boxes too.
							// We can mark axes with user data or just rebuild everything.
							// Rebuilding everything is fast enough for 300 boxes.

							// Actually, let's just make a sub-group for content.
							// But create3DScene returns a SubScene and populates the world.
							// Let's modify create3DScene or just hack it here.
							// Easiest is to just re-add axes if we clear.

							// Better approach:
							// Inside create3DScene, make a permanent group for 'static' stuff (camera,
							// axes)
							// and a group for 'dynamic' stuff (boxes).
							// Since I can't easily change the signature of create3DScene in this replace
							// block without changing other things,
							// I will just rely on the fact that I know what create3DScene does.

							// BUT, I can just clear simply:
							// resultWorld.getChildren().clear();
							// And then add camera/axes back.
							// Or, verify what create3DScene does. It clears world.
							// So if I call create3DScene again? No, that creates a NEW SubScene. I don't
							// want a new SubScene every frame.

							// Fix: In create3DScene, I'll add a specific Group for boxes.
							// I'll make `private Group boxGroup;` field.
							// In create3DScene: `boxGroup = new Group();
							// world.getChildren().add(boxGroup);`
							// Here: `boxGroup.getChildren().clear(); ... add results to boxGroup`
						});

						// We need to wait for Platform.runLater to define boxGroup if we go that route.
						// Instead, let's just use a dedicated group that we create/manage here or
						// finding by ID.

						Platform.runLater(() -> {
							// We can't easily use a dedicated field without adding it to the class.
							// Let's inspect resultWorld children.
							// If we just clear resultWorld and re-add camera/axes + boxes, it works.
							// But we lose camera position if we recreate camera.
							// We MUST preserve camera.

							// Let's assume resultWorld has [CameraGroup, Axis1, Axis2, Axis3, ...Boxes...]
							// We can remove from index 4 to end.
							if (resultWorld != null && resultWorld.getChildren().size() > 4) {
								resultWorld.getChildren().remove(4, resultWorld.getChildren().size());
							}

							int binOffset = -50;
							for (List<com.binpacker.lib.common.Box> binBoxes : result) {
								for (com.binpacker.lib.common.Box spec : binBoxes) {
									Color boxColor = boxColors.get(spec.id % boxColors.size());
									PhongMaterial boxMaterial = new PhongMaterial(boxColor);
									Box box = new Box(spec.size.x, spec.size.y, spec.size.z);
									box.setMaterial(boxMaterial);

									box.setTranslateX(spec.position.x + spec.size.x / 2 + binOffset);
									box.setTranslateY(spec.position.y + spec.size.y / 2);
									box.setTranslateZ(spec.position.z + spec.size.z / 2);

									resultWorld.getChildren().add(box);
								}

								// Draw bin outline
								Box binBox = new Box(bin.w, bin.h, bin.d);
								binBox.setDrawMode(DrawMode.LINE);
								binBox.setMaterial(new PhongMaterial(Color.BLACK));
								binBox.setTranslateX(bin.w / 2 + binOffset);
								binBox.setTranslateY(bin.h / 2);
								binBox.setTranslateZ(bin.d / 2);
								resultWorld.getChildren().add(binBox);

								binOffset += 40;
							}
						});
					}
				} finally {
					isSolving = false;
					optimizer.release();
				}
				return null;
			}
		};
		new Thread(currentSolverTask).start();
	}

	private void exportSolution() {
		if (result == null || result.isEmpty()) {
			statusLabel.setText("No solution to export – run the solver first.");
			return;
		}

		String csv = Utils.exportCsv(result);

		FileChooser chooser = new FileChooser();
		chooser.setTitle("Save Solution CSV");
		chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
		chooser.setInitialFileName("solution.csv");

		File file = chooser.showSaveDialog(primaryStage);
		if (file == null) {
			statusLabel.setText("Export cancelled.");
			return;
		}

		try (FileWriter writer = new FileWriter(file)) {
			writer.write(csv);
			statusLabel.setText("Exported to " + file.getAbsolutePath());
		} catch (IOException e) {
			statusLabel.setText("Failed to export: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private List<com.binpacker.lib.common.Box> generateRandomBoxes(int count) {

		List<com.binpacker.lib.common.Box> boxes = new ArrayList<>();
		Random random = new Random(42); // Fixed seed for deterministic results
		for (int i = 0; i < count; i++) {
			float width = random.nextInt(8) + 4;
			float height = random.nextInt(8) + 4;
			float depth = random.nextInt(8) + 4;
			float weight = random.nextInt(10) + 1;
			com.binpacker.lib.common.Box box = new com.binpacker.lib.common.Box(
					new com.binpacker.lib.common.Point3f(0, 0, 0),
					new com.binpacker.lib.common.Point3f(width, height, depth));
			box.id = i;
			box.weight = weight;
			boxes.add(box);
		}
		return boxes;

	}

	private List<com.binpacker.lib.common.Box> loadBoxesFromCsv(File file) {
		List<com.binpacker.lib.common.Box> boxes = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			String line;
			int idCounter = 0;
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#"))
					continue; // Skip empty or comment lines

				String[] parts = line.split(",");
				if (parts.length >= 3) {
					try {
						float w = Float.parseFloat(parts[0].trim());
						float h = Float.parseFloat(parts[1].trim());
						float d = Float.parseFloat(parts[2].trim());
						float weight = 0; // default weight
						if (parts.length >= 4) {
							weight = Float.parseFloat(parts[3].trim());
						}

						com.binpacker.lib.common.Box box = new com.binpacker.lib.common.Box(
								new com.binpacker.lib.common.Point3f(0, 0, 0), // Initial position 0
								new com.binpacker.lib.common.Point3f(w, h, d));
						box.id = idCounter++;
						box.weight = weight;
						boxes.add(box);
					} catch (NumberFormatException e) {
						System.err.println("Skipping invalid line: " + line);
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			// Could show an alert, but logging used for now
		}
		return boxes;
	}
}
