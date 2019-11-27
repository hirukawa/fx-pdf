package net.osdn.util.fx.pdf;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

import java.util.HashMap;
import java.util.Map;

public class Pager extends HBox {
	
	private static final double HEIGHT = 25.0;

	private IntegerProperty pageIndexProperty
		= new SimpleIntegerProperty(this, "pageIndex");
	
	public IntegerProperty pageIndexProperty() {
		return pageIndexProperty;
	}
	public final int getPageIndex() {
		return pageIndexProperty.get();
	}
	public final void setPageIndex(int value) {
		pageIndexProperty.set(value);
	}
	
	private IntegerProperty maxPageIndexProperty
		= new SimpleIntegerProperty(this, "maxPageIndex");
	
	public IntegerProperty maxPageIndexProperty() {
		return maxPageIndexProperty;
	}
	public final int getMaxPageIndex() {
		return maxPageIndexProperty.get();
	}
	
	private Label lblPageNumber = new Label();
	private Button btnFirst;
	private Button btnPrevious;
	private Button btnNext;
	private Button btnLast;
	
	public Pager() {
		getStylesheets().add(getClass().getResource("Pager.css").toExternalForm());
		getStyleClass().add("pager");
		
		btnFirst = new PageButton(
				new Path(
					new MoveTo(HEIGHT * 0.70, HEIGHT * 0.30),
					new LineTo(HEIGHT * 0.50, HEIGHT * 0.50),
					new LineTo(HEIGHT * 0.70, HEIGHT * 0.70),
					new MoveTo(HEIGHT * 0.35, HEIGHT * 0.30),
					new LineTo(HEIGHT * 0.35, HEIGHT * 0.70)
				),
				action -> pageIndexProperty.set(0));
		
		btnPrevious = new PageButton(
				new Path(
					new MoveTo(HEIGHT * 0.70, HEIGHT * 0.30),
					new LineTo(HEIGHT * 0.50, HEIGHT * 0.50),
					new LineTo(HEIGHT * 0.70, HEIGHT * 0.70)
				),
				action -> pageIndexProperty.set(pageIndexProperty.get() - 1));
		
		btnNext = new PageButton(
				new Path(
					new MoveTo(HEIGHT * 0.30, HEIGHT * 0.30),
					new LineTo(HEIGHT * 0.50, HEIGHT * 0.50),
					new LineTo(HEIGHT * 0.30, HEIGHT * 0.70)
				),
				action -> pageIndexProperty.set(pageIndexProperty.get() + 1));
		
		btnLast = new PageButton(
				new Path(
					new MoveTo(HEIGHT * 0.30, HEIGHT * 0.30),
					new LineTo(HEIGHT * 0.50, HEIGHT * 0.50),
					new LineTo(HEIGHT * 0.30, HEIGHT * 0.70),
					new MoveTo(HEIGHT * 0.65, HEIGHT * 0.30),
					new LineTo(HEIGHT * 0.65, HEIGHT * 0.70)
				),
				action -> pageIndexProperty.set(maxPageIndexProperty.get()));
		
		getChildren().addAll(
				btnFirst,
				btnPrevious,
				lblPageNumber,
				btnNext,
				btnLast);
		
		pageIndexProperty.addListener((observable, oldValue, newValue) -> {
			update();
		});
		maxPageIndexProperty.addListener((observable, oldValue, newValue) -> {
			update();
		});
	}
	
	protected void update() {
		if(maxPageIndexProperty.get() <= 0) {
			lblPageNumber.setText("");
			btnFirst.setDisable(true);
			btnPrevious.setDisable(true);
			btnNext.setDisable(true);
			btnLast.setDisable(true);
		} else {
			lblPageNumber.setText((pageIndexProperty.get() + 1) + " / " + (maxPageIndexProperty.get() + 1));
			btnFirst.setDisable(pageIndexProperty.get() <= 0);
			btnPrevious.setDisable(pageIndexProperty.get() <= 0);
			btnNext.setDisable(pageIndexProperty.get() == maxPageIndexProperty.get());
			btnLast.setDisable(pageIndexProperty.get() == maxPageIndexProperty.get());
		}
	}
	
	public void moveFirst() {
		btnFirst.fire();
	}
	
	public void movePrevious() {
		btnPrevious.fire();
	}
	
	public void moveNext() {
		btnNext.fire();
	}
	
	public void moveLast() {
		btnLast.fire();
	}
	
	
	/** このページャーの既定のアクセラレータリストを作成します。
	 * 
	 * Sceneのアクセラレータのリストに追加することで、キーボード操作によるページ移動が有効になります。
	 * 
	 * <pre>{@code
	 * scene.getAccelerators().putAll(pager.getDefaultAccelerators());
     * }</pre>
	 * 
	 * @return ページ移動を可能にするアクセラレータのリスト。
	 */
	public Map<KeyCombination, Runnable> createDefaultAccelerators() {
		
		Map<KeyCombination, Runnable> accelerators = new HashMap<KeyCombination, Runnable>();

		accelerators.put(new KeyCodeCombination(KeyCode.HOME),      ()-> moveFirst());
		
		accelerators.put(new KeyCodeCombination(KeyCode.PAGE_UP),   ()-> movePrevious());
		accelerators.put(new KeyCodeCombination(KeyCode.UP),        ()-> movePrevious());
		accelerators.put(new KeyCodeCombination(KeyCode.KP_UP),     ()-> movePrevious());
		accelerators.put(new KeyCodeCombination(KeyCode.LEFT),      ()-> movePrevious());
		accelerators.put(new KeyCodeCombination(KeyCode.KP_LEFT),   ()-> movePrevious());
		
		accelerators.put(new KeyCodeCombination(KeyCode.PAGE_DOWN), ()-> moveNext());
		accelerators.put(new KeyCodeCombination(KeyCode.DOWN),      ()-> moveNext());
		accelerators.put(new KeyCodeCombination(KeyCode.KP_DOWN),   ()-> moveNext());
		accelerators.put(new KeyCodeCombination(KeyCode.RIGHT),     ()-> moveNext());
		accelerators.put(new KeyCodeCombination(KeyCode.KP_RIGHT),  ()-> moveNext());
		
		accelerators.put(new KeyCodeCombination(KeyCode.END),       ()-> moveLast());
		
		return accelerators;
	}

	private class PageButton extends Button {
		public PageButton(Path path, EventHandler<ActionEvent> action) {
			setMinHeight(HEIGHT);
			setPrefHeight(HEIGHT);
			setDisable(true);
			setFocusTraversable(false);

			path.strokeProperty().bind(textFillProperty());
			path.setStrokeWidth(1.2);
			path.setStrokeLineCap(StrokeLineCap.ROUND);
			path.setStrokeLineJoin(StrokeLineJoin.ROUND);
			
			setGraphic(path);
			setOnAction(action);
		}
	}
}
