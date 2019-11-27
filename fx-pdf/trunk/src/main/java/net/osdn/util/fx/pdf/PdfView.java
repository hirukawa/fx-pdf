package net.osdn.util.fx.pdf;

import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PdfView extends Region {

	private ObjectProperty<PDDocument> documentProperty
		= new SimpleObjectProperty<PDDocument>(this, "document");
	
	public ObjectProperty<PDDocument> documentProperty() {
		return documentProperty;
	}
	public final PDDocument getDocument() {
		return documentProperty.get();
	}
	public final void setDocument(PDDocument value) {
		documentProperty.set(value);
	}

	
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

	public ReadOnlyIntegerProperty maxPageIndexProperty() {
		return maxPageIndexProperty;
	}
	public final int getMaxPageIndex() {
		return maxPageIndexProperty.get();
	}
	public final void setMaxPageIndex(int value) {
		maxPageIndexProperty.set(value);
	}

	
	
	private ProgressIndicator progressIndicator;
	private Canvas canvas;
	private boolean isDirty;
	private boolean isBusy;

	private ExecutorService worker;
	private double width;
	private double height;
	private PDDocument document;
	private int pageIndex;
	
	private BufferedImage bimg;
	private WritableImage wimg;
	
	public PdfView() {
		worker = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r);
			t.setDaemon(true);
			return t;
		});
		
		canvas = new Canvas();
		canvas.widthProperty().bind(widthProperty());
		canvas.heightProperty().bind(heightProperty());
		getChildren().add(canvas);

		progressIndicator = new ProgressIndicator();
		progressIndicator.setVisible(false);
		getChildren().add(progressIndicator);
		
		documentProperty.addListener((observable, oldValue, newValue) -> {
			pageIndexProperty.set(0);
			if(newValue == null) {
				maxPageIndexProperty.set(0);
			} else {
				maxPageIndexProperty.set(newValue.getNumberOfPages() - 1);
			}
			update();
		});
		
		pageIndexProperty.addListener((observable, oldValue, newValue) -> {
			update();
		});
		widthProperty().addListener((observable, oldValue, newValue) -> {
			update();
		});
		heightProperty().addListener((observable, oldValue, newValue) -> {
			update();
		});
	}
	
	public void update() {
		isDirty = true;
		
		if(!isBusy) {
			isBusy = true;
			isDirty = false;
			
			width = getWidth();
			height = getHeight();
			document = documentProperty.get();
			pageIndex = pageIndexProperty.get();
			
			worker.submit(() -> {
				WritableImage img = prepare();
				Platform.runLater(() -> {
					GraphicsContext gc = canvas.getGraphicsContext2D();
					gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
					if(img != null) {
						double x = (canvas.getWidth() - img.getWidth()) / 2;
						double y = (canvas.getHeight() - img.getHeight()) / 2;
						gc.drawImage(img, x, y);
					}
					isBusy = false;
					if(isDirty) {
						update();
					}
				});
			});
		}
	}
	
	protected WritableImage prepare() {
		if(document == null) {
			return null;
		}
		PDRectangle paper = document.getPage(pageIndex).getCropBox();
		double w;
		double h;
		if(paper.getWidth() / paper.getHeight() < width / height) {
			w = height * paper.getWidth() / paper.getHeight();
			h = height;
		} else {
			w = width;
			h = width * paper.getHeight() / paper.getWidth();
		}
		float scale = (float)(h / paper.getHeight());
		
		if(bimg == null || bimg.getWidth() != (int)w || bimg.getHeight() != (int)h) {
			bimg = new BufferedImage((int)w, (int)h, BufferedImage.TYPE_INT_RGB);
			wimg = new WritableImage((int)w, (int)h);
		}
		Graphics2D graphics = null;
		try {
			graphics = bimg.createGraphics();
			graphics.setBackground(Color.WHITE);
			graphics.clearRect(0, 0, (int)w, (int)h);
			
			PDFRenderer renderer = new PDFRenderer(document);
			renderer.renderPageToGraphics(pageIndex, graphics, scale);
			return SwingFXUtils.toFXImage(bimg, wimg);
		} catch(IOException e) {
			throw new RuntimeException(e);
		} finally {
			if(graphics != null) {
				graphics.dispose();
			}
		}
	}
	
	
	@Override
	protected void layoutChildren() {
		progressIndicator.relocate(
				(getWidth() - progressIndicator.getWidth()) / 2,
				(getHeight() - progressIndicator.getHeight()) / 2);
		
		super.layoutChildren();
	}
	
	public Task<PDDocument> load(Callable<PDDocument> loader) {
		Task<PDDocument> task = new Task<PDDocument>() {
			@Override
			protected PDDocument call() throws Exception {
				try {
					PDDocument document = loader.call();

					// フォントを読み込ませるために最大10ページを事前にレンダリングします。
					Graphics2D graphics = null;
					try {
						PDFRenderer renderer = new PDFRenderer(document);
						BufferedImage bimg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
						graphics = bimg.createGraphics();
						int max = Math.min(10, document.getNumberOfPages());
						for(int i = 0; i < max; i++) {
							renderer.renderPageToGraphics(i, graphics);
						}
					} finally {
						if(graphics != null) {
							graphics.dispose();
						}
					}
					
					Platform.runLater(() -> {
						setDocument(document);
					});
					return document;
				} finally {
					Platform.runLater(() -> {
						progressIndicator.setVisible(false);
					});
				}
			}
		};

		if(Platform.isFxApplicationThread()) {
			setDocument(null);
			progressIndicator.setVisible(true);
			worker.execute(task);
		} else {
			try {
				runAndWait(() -> {
					setDocument(null);
					progressIndicator.setVisible(true);
				});
				task.run();
			} catch(Throwable t) {
				throw new RuntimeException(t);
			}
		}
		return task;
	}
	
	public Task<PDDocument> load(File file) {
		return load(() -> PDDocument.load(file));
	}
	
	public Task<PDDocument> load(File file, MemoryUsageSetting memUsageSetting) {
		return load(() -> PDDocument.load(file, memUsageSetting));
	}
	
	public Task<PDDocument> load(File file, String password) {
		return load(() -> PDDocument.load(file, password));
	}
	
	public Task<PDDocument> load(File file, String password, MemoryUsageSetting memUsageSetting) {
		return load(() -> PDDocument.load(file, password, memUsageSetting));
	}
	
	public Task<PDDocument> load(File file, String password, InputStream keyStore, String alias) {
		return load(() -> PDDocument.load(file, password, keyStore, alias));
	}
	
	public Task<PDDocument> load(File file, String password, InputStream keyStore, String alias, MemoryUsageSetting memUsageSetting) {
		return load(() -> PDDocument.load(file, password, keyStore, alias, memUsageSetting));
	}
	
	public Task<PDDocument> load(InputStream input) {
		return load(() -> PDDocument.load(input));
	}
	
	public Task<PDDocument> load(InputStream input, MemoryUsageSetting memUsageSetting) {
		return load(() -> PDDocument.load(input, memUsageSetting));
	}
	
	public Task<PDDocument> load(InputStream input, String password) {
		return load(() -> PDDocument.load(input, password));
	}
	
	public Task<PDDocument> load(InputStream input, String password, InputStream keyStore, String alias) {
		return load(() -> PDDocument.load(input, password, keyStore, alias));
	}
	
	public Task<PDDocument> load(InputStream input, String password, MemoryUsageSetting memUsageSetting) {
		return load(() -> PDDocument.load(input, password, memUsageSetting));
	}
	
	public Task<PDDocument> load(InputStream input, String password, InputStream keyStore, String alias, MemoryUsageSetting memUsageSetting) {
		return load(() -> PDDocument.load(input, password, keyStore, alias, memUsageSetting));
	}
	
	public Task<PDDocument> load(byte[] input) {
		return load(() -> PDDocument.load(input));
	}
	
	public Task<PDDocument> load(byte[] input, String password) {
		return load(() -> PDDocument.load(input, password));
	}
	
	public Task<PDDocument> load(byte[] input, String password, InputStream keyStore, String alias) {
		return load(() -> PDDocument.load(input, password, keyStore, alias));
	}
	
	public Task<PDDocument> load(byte[] input, String password, InputStream keyStore, String alias, MemoryUsageSetting memUsageSetting) {
		return load(() -> PDDocument.load(input, password, keyStore, alias, memUsageSetting));
	}
	
	protected static void runAndWait(Runnable runnable) throws InterruptedException, InvocationTargetException {
		if(Platform.isFxApplicationThread()) {
            throw new Error("Cannot call runAndWait from the FX Application Thread");
		}
		
		Throwable[] throwable = new Throwable[1];
		CountDownLatch latch = new CountDownLatch(1);
		Platform.runLater(() -> {
			try {
				runnable.run();
			} catch(Throwable t) {
				throwable[0] = t;
			} finally {
				latch.countDown();
			}
			latch.countDown();
		});
		latch.await();
		if(throwable[0] != null) {
			throw new InvocationTargetException(throwable[0]);
		}
	}
}
