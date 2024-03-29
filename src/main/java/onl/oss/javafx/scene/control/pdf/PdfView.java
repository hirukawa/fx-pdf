package onl.oss.javafx.scene.control.pdf;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.stage.Screen;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PdfView extends Region {

	public final ObjectProperty<PDDocument> documentProperty() {
		if (document == null) {
			document = new SimpleObjectProperty<>(this, "document");
		}
		return document;
	}

	private ObjectProperty<PDDocument> document;

	public final PDDocument getDocument() {
		return documentProperty().get();
	}

	public final void setDocument(PDDocument value) {
		initialPageIndex = 0;
		documentProperty().set(value);
	}

	public final void setDocument(PDDocument value, int pageIndex) {
		if (value == null || pageIndex < 0) {
			initialPageIndex = 0;
		} else if (pageIndex >= value.getNumberOfPages()) {
			initialPageIndex = value.getNumberOfPages() - 1;
		} else {
			initialPageIndex = pageIndex;
		}
		documentProperty().set(value);
	}

	public final IntegerProperty pageIndexProperty() {
		if (pageIndex == null) {
			pageIndex = new SimpleIntegerProperty(this, "pageIndex");
		}
		return pageIndex;
	}

	private IntegerProperty pageIndex;

	public final int getPageIndex() {
		return pageIndexProperty().get();
	}

	public final void setPageIndex(int value) {
		pageIndexProperty().set(value);
	}

	public final ReadOnlyIntegerProperty maxPageIndexProperty() {
		return maxPageIndexPropertyInternal();
	}

	private IntegerProperty maxPageIndexPropertyInternal() {
		if (maxPageIndex == null) {
			maxPageIndex = new SimpleIntegerProperty(this, "maxPageIndex");
		}
		return maxPageIndex;
	}

	private IntegerProperty maxPageIndex;

	public final int getMaxPageIndex() {
		return maxPageIndexProperty().get();
	}

	public final ReadOnlyDoubleProperty renderScaleProperty() {
		return renderScalePropertyInternal();
	}

	private DoubleProperty renderScalePropertyInternal() {
		if (renderScale == null) {
			renderScale = new SimpleDoubleProperty(this, "renderScale");
		}
		return renderScale;
	}

	private DoubleProperty renderScale;

	public final double getRenderScale() {
		return renderScaleProperty().get();
	}

	public ReadOnlyObjectProperty<Rectangle2D> renderBoundsProperty() {
		return renderBoundsPropertyInternal();
	}

	private ObjectProperty<Rectangle2D> renderBoundsPropertyInternal() {
		if (renderBounds == null) {
			renderBounds = new SimpleObjectProperty<>(this, "renderBounds", Rectangle2D.EMPTY);
		}
		return renderBounds;
	}

	private ObjectProperty<Rectangle2D> renderBounds;

	public Rectangle2D getRenderBounds() {
		return renderBoundsProperty().get();
	}

	private RenderingHints renderingHints;

	private ProgressIndicator progressIndicator;
	private ImageView imageView;

	private ExecutorService worker;
	private int initialPageIndex;

	public PdfView() {
		worker = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r);
			t.setDaemon(true);
			return t;
		});

		Rectangle clip = new Rectangle();
		clip.widthProperty().bind(widthProperty());
		clip.heightProperty().bind(heightProperty());
		setClip(clip);

		imageView = new ImageView();
		imageView.setPreserveRatio(true);
		getChildren().add(imageView);

		progressIndicator = new ProgressIndicator();
		progressIndicator.setVisible(false);
		getChildren().add(progressIndicator);

		documentProperty().addListener((observable, oldValue, newValue) -> {
			pageIndexProperty().set(initialPageIndex);
			if (newValue == null) {
				maxPageIndexPropertyInternal().set(0);
			} else {
				maxPageIndexPropertyInternal().set(newValue.getNumberOfPages() - 1);
			}
			updatePage();
		});

		pageIndexProperty().addListener((observable, oldValue, newValue) -> {
			updatePage();
		});
		widthProperty().addListener((observable, oldValue, newValue) -> {
			updateSize();
		});
		heightProperty().addListener((observable, oldValue, newValue) -> {
			updateSize();
		});
	}

	public void setRenderingHints(RenderingHints hints) {
		renderingHints = hints;
	}

	public RenderingHints getRenderingHints() {
		return renderingHints;
	}

	public void updatePage() {
		update();
	}

	public void updateSize() {
		update();
	}

	private volatile boolean isBusy = false;
	private volatile boolean isFollowed = false;

	private double paperWidth = 0.0;
	private double paperHeight = 0.0;

	/** ImageView を PdfView の中央に表示されるようにします。
	 *
	 */
	private void adjustCenter() {
		if(paperWidth > 0.0 && paperHeight > 0.0) {
			double pdfViewWidth = getWidth();
			double pdfViewHeight = getHeight();
			double w;
			double h;
			if (paperWidth / paperHeight < pdfViewWidth / pdfViewHeight) {
				w = pdfViewHeight * paperWidth / paperHeight;
				h = pdfViewHeight;
			} else {
				w = pdfViewWidth;
				h = pdfViewWidth * paperHeight / paperWidth;
			}
			double x = (pdfViewWidth - w) / 2;
			double y = (pdfViewHeight - h) / 2;
			double scale = h / paperHeight;
			imageView.setX(x);
			imageView.setY(y);
			imageView.setFitWidth(w);
			imageView.setFitHeight(h);
			renderScalePropertyInternal().set(scale);
			renderBoundsPropertyInternal().set(new Rectangle2D(x, y, w, h));
		} else {
			imageView.setX(0.0);
			imageView.setY(0.0);
			imageView.setFitWidth(0.0);
			imageView.setFitHeight(0.0);
			renderScalePropertyInternal().set(0.0);
			renderBoundsPropertyInternal().set(Rectangle2D.EMPTY);
		}
	}

	private void update() {
		synchronized (worker) {
			if(isFollowed) {
				adjustCenter();
				return;
			}
			if(isBusy) {
				isFollowed = true;
				adjustCenter();
				return;
			}
			isBusy = true;
		}

		int pageIndex = getPageIndex();
		PDDocument document = getDocument();
		if(document == null) {
			paperWidth = 0.0;
			paperHeight = 0.0;
			imageView.setImage(null);
		} else {
			PDRectangle paper = document.getPage(pageIndex).getCropBox();
			int rotation = document.getPage(pageIndex).getRotation();
			paperWidth = (rotation % 180 == 0) ? paper.getWidth() : paper.getHeight();
			paperHeight = (rotation % 180 == 0) ? paper.getHeight() : paper.getWidth();
		}
		adjustCenter();

		RenderingHints hints = getRenderingHints();

		Screen screen = getScreen(this);
		if(screen == null) {
			screen = Screen.getPrimary();
		}
		double width = getWidth() * screen.getOutputScaleX();
		double height = getHeight() * screen.getOutputScaleY();

		worker.submit(() -> {
			WritableImage image = createImage(document, pageIndex, hints, width, height);

			Platform.runLater(() -> {
				if(image != null) {
					imageView.setImage(image);
				}
			});

			synchronized (worker) {
				if(isFollowed) {
					isFollowed = false;
					isBusy = false;
					Platform.runLater(() -> {
						update();
					});
				} else {
					isBusy = false;
				}
			}
		});
	}

	private BufferedImage bimg;
	private WritableImage wimg;

	private WritableImage createImage(PDDocument document, int pageIndex, RenderingHints renderingHints, double width, double height) {
		if (document == null) {
			return null;
		}
		PDRectangle paper = document.getPage(pageIndex).getCropBox();
		int rotation = document.getPage(pageIndex).getRotation();
		double paperWidth = (rotation % 180 == 0) ? paper.getWidth() : paper.getHeight();
		double paperHeight = (rotation % 180 == 0) ? paper.getHeight() : paper.getWidth();

		double w;
		double h;
		if (paperWidth / paperHeight < width / height) {
			w = height * paperWidth / paperHeight;
			h = height;
		} else {
			w = width;
			h = width * paperHeight / paperWidth;
		}
		double scale = h / paperHeight;

		if (bimg == null || bimg.getWidth() != (int) w || bimg.getHeight() != (int) h) {
			bimg = new BufferedImage((int) w, (int) h, BufferedImage.TYPE_INT_RGB);
			wimg = new WritableImage((int) w, (int) h);
		}
		Graphics2D graphics = null;
		try {
			graphics = bimg.createGraphics();
			graphics.setBackground(Color.WHITE);
			graphics.clearRect(0, 0, (int) w, (int) h);

			PDFRenderer renderer = new PDFRenderer(document);
			if (renderingHints != null) {
				renderer.setRenderingHints(renderingHints);
			}
			renderer.renderPageToGraphics(pageIndex, graphics, (float)scale);
			return SwingFXUtils.toFXImage(bimg, wimg);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			if (graphics != null) {
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
		return load(loader, 0);
	}

	public Task<PDDocument> load(Callable<PDDocument> loader, final int initialPageIndex) {
		Task<PDDocument> task = new Task<PDDocument>() {
			@Override
			protected PDDocument call() throws Exception {
				Exception exception = null;
				try {
					PDDocument document = loader.call();

					// フォントを読み込ませるために最大10ページを事前にレンダリングします。
					Graphics2D graphics = null;
					try {
						PDFRenderer renderer = new PDFRenderer(document);
						BufferedImage bimg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
						graphics = bimg.createGraphics();
						int max = Math.min(10, document.getNumberOfPages());
						for (int i = 0; i < max; i++) {
							renderer.renderPageToGraphics(i, graphics);
						}
					} finally {
						if (graphics != null) {
							graphics.dispose();
						}
					}

					Platform.runLater(() -> {
						setDocument(document, initialPageIndex);
					});
					return document;
				} catch(Exception e) {
					exception = e;
				} finally {
					final Exception e = exception;

					Platform.runLater(() -> {
						progressIndicator.setVisible(false);

						// ワーカースレッドで例外が発生していた場合、UIスレッドでその例外をスローします。
						if(e != null) {
							if(e instanceof RuntimeException) {
								throw (RuntimeException)e;
							} else {
								throw new RuntimeException(e);
							}
						}
					});
				}
				return null;
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
		// PDDocument.loadにFileを渡すとファイルがオープンされたままになり
		// 上書き保存できなくなってしまうため、先にバイト列を取得してそれをPDDocument.loadに渡します。
		return load(() -> {
			try(InputStream is = new FileInputStream(file)) {
				return PDDocument.load(is.readAllBytes());
			}
		}, 0);
	}

	public Task<PDDocument> load(File file, int initialPageIndex) {
		// PDDocument.loadにFileを渡すとファイルがオープンされたままになり
		// 上書き保存できなくなってしまうため、先にバイト列を取得してそれをPDDocument.loadに渡します。
		return load(() -> {
			try(InputStream is = new FileInputStream(file)) {
				return PDDocument.load(is.readAllBytes());
			}
		}, initialPageIndex);
	}

	public Task<PDDocument> load(File file, MemoryUsageSetting memUsageSetting) {
		// PDDocument.loadにFileを渡すとファイルがオープンされたままになり
		// 上書き保存できなくなってしまうため、先にバイト列を取得してそれをPDDocument.loadに渡します。
		return load(() -> {
			try(InputStream is = new FileInputStream(file)) {
				return PDDocument.load(is.readAllBytes(), "", null, null, memUsageSetting);
			}
		}, 0);
	}

	public Task<PDDocument> load(File file, int initialPageIndex, MemoryUsageSetting memUsageSetting) {
		// PDDocument.loadにFileを渡すとファイルがオープンされたままになり
		// 上書き保存できなくなってしまうため、先にバイト列を取得してそれをPDDocument.loadに渡します。
		return load(() -> {
			try(InputStream is = new FileInputStream(file)) {
				return PDDocument.load(is.readAllBytes(), "", null, null, memUsageSetting);
			}
		}, initialPageIndex);
	}

	public Task<PDDocument> load(File file, String password) {
		// PDDocument.loadにFileを渡すとファイルがオープンされたままになり
		// 上書き保存できなくなってしまうため、先にバイト列を取得してそれをPDDocument.loadに渡します。
		return load(() -> {
			try(InputStream is = new FileInputStream(file)) {
				return PDDocument.load(is.readAllBytes(), password);
			}
		}, 0);
	}

	public Task<PDDocument> load(File file, int initialPageIndex, String password) {
		// PDDocument.loadにFileを渡すとファイルがオープンされたままになり
		// 上書き保存できなくなってしまうため、先にバイト列を取得してそれをPDDocument.loadに渡します。
		return load(() -> {
			try(InputStream is = new FileInputStream(file)) {
				return PDDocument.load(is.readAllBytes(), password);
			}
		}, initialPageIndex);
	}

	public Task<PDDocument> load(File file, String password, MemoryUsageSetting memUsageSetting) {
		// PDDocument.loadにFileを渡すとファイルがオープンされたままになり
		// 上書き保存できなくなってしまうため、先にバイト列を取得してそれをPDDocument.loadに渡します。
		return load(() -> {
			try(InputStream is = new FileInputStream(file)) {
				return PDDocument.load(is.readAllBytes(), password, null, null, memUsageSetting);
			}
		}, 0);
	}

	public Task<PDDocument> load(File file, int initialPageIndex, String password, MemoryUsageSetting memUsageSetting) {
		// PDDocument.loadにFileを渡すとファイルがオープンされたままになり
		// 上書き保存できなくなってしまうため、先にバイト列を取得してそれをPDDocument.loadに渡します。
		return load(() -> {
			try(InputStream is = new FileInputStream(file)) {
				return PDDocument.load(is.readAllBytes(), password, null, null, memUsageSetting);
			}
		}, initialPageIndex);
	}

	public Task<PDDocument> load(File file, String password, InputStream keyStore, String alias) {
		// PDDocument.loadにFileを渡すとファイルがオープンされたままになり
		// 上書き保存できなくなってしまうため、先にバイト列を取得してそれをPDDocument.loadに渡します。
		return load(() -> {
			try(InputStream is = new FileInputStream(file)) {
				return PDDocument.load(is.readAllBytes(), password, keyStore, alias);
			}
		}, 0);
	}

	public Task<PDDocument> load(File file, int initialPageIndex, String password, InputStream keyStore, String alias) {
		// PDDocument.loadにFileを渡すとファイルがオープンされたままになり
		// 上書き保存できなくなってしまうため、先にバイト列を取得してそれをPDDocument.loadに渡します。
		return load(() -> {
			try(InputStream is = new FileInputStream(file)) {
				return PDDocument.load(is.readAllBytes(), password, keyStore, alias);
			}
		}, initialPageIndex);
	}

	public Task<PDDocument> load(File file, String password, InputStream keyStore, String alias, MemoryUsageSetting memUsageSetting) {
		// PDDocument.loadにFileを渡すとファイルがオープンされたままになり
		// 上書き保存できなくなってしまうため、先にバイト列を取得してそれをPDDocument.loadに渡します。
		return load(() -> {
			try(InputStream is = new FileInputStream(file)) {
				return PDDocument.load(is.readAllBytes(), password, keyStore, alias, memUsageSetting);
			}
		}, 0);
	}

	public Task<PDDocument> load(File file, int initialPageIndex, String password, InputStream keyStore, String alias, MemoryUsageSetting memUsageSetting) {
		// PDDocument.loadにFileを渡すとファイルがオープンされたままになり
		// 上書き保存できなくなってしまうため、先にバイト列を取得してそれをPDDocument.loadに渡します。
		return load(() -> {
			try(InputStream is = new FileInputStream(file)) {
				return PDDocument.load(is.readAllBytes(), password, keyStore, alias, memUsageSetting);
			}
		}, initialPageIndex);
	}

	public Task<PDDocument> load(InputStream input) {
		return load(() -> PDDocument.load(input), 0);
	}

	public Task<PDDocument> load(InputStream input, int initialPageIndex) {
		return load(() -> PDDocument.load(input), initialPageIndex);
	}

	public Task<PDDocument> load(InputStream input, MemoryUsageSetting memUsageSetting) {
		return load(() -> PDDocument.load(input, memUsageSetting), 0);
	}

	public Task<PDDocument> load(InputStream input, int initialPageIndex, MemoryUsageSetting memUsageSetting) {
		return load(() -> PDDocument.load(input, memUsageSetting), initialPageIndex);
	}

	public Task<PDDocument> load(InputStream input, String password) {
		return load(() -> PDDocument.load(input, password), 0);
	}

	public Task<PDDocument> load(InputStream input, int initialPageIndex, String password) {
		return load(() -> PDDocument.load(input, password), initialPageIndex);
	}

	public Task<PDDocument> load(InputStream input, String password, InputStream keyStore, String alias) {
		return load(() -> PDDocument.load(input, password, keyStore, alias), 0);
	}

	public Task<PDDocument> load(InputStream input, int initialPageIndex, String password, InputStream keyStore, String alias) {
		return load(() -> PDDocument.load(input, password, keyStore, alias), initialPageIndex);
	}

	public Task<PDDocument> load(InputStream input, String password, MemoryUsageSetting memUsageSetting) {
		return load(() -> PDDocument.load(input, password, memUsageSetting), 0);
	}

	public Task<PDDocument> load(InputStream input, int initialPageIndex, String password, MemoryUsageSetting memUsageSetting) {
		return load(() -> PDDocument.load(input, password, memUsageSetting), initialPageIndex);
	}

	public Task<PDDocument> load(InputStream input, String password, InputStream keyStore, String alias, MemoryUsageSetting memUsageSetting) {
		return load(() -> PDDocument.load(input, password, keyStore, alias, memUsageSetting), 0);
	}

	public Task<PDDocument> load(InputStream input, int initialPageIndex, String password, InputStream keyStore, String alias, MemoryUsageSetting memUsageSetting) {
		return load(() -> PDDocument.load(input, password, keyStore, alias, memUsageSetting), initialPageIndex);
	}

	public Task<PDDocument> load(byte[] input) {
		return load(() -> PDDocument.load(input), 0);
	}

	public Task<PDDocument> load(byte[] input, int initialPageIndex) {
		return load(() -> PDDocument.load(input), initialPageIndex);
	}

	public Task<PDDocument> load(byte[] input, String password) {
		return load(() -> PDDocument.load(input, password), 0);
	}

	public Task<PDDocument> load(byte[] input, int initialPageIndex, String password) {
		return load(() -> PDDocument.load(input, password), initialPageIndex);
	}

	public Task<PDDocument> load(byte[] input, String password, InputStream keyStore, String alias) {
		return load(() -> PDDocument.load(input, password, keyStore, alias), 0);
	}

	public Task<PDDocument> load(byte[] input, int initialPageIndex, String password, InputStream keyStore, String alias) {
		return load(() -> PDDocument.load(input, password, keyStore, alias), initialPageIndex);
	}

	public Task<PDDocument> load(byte[] input, String password, InputStream keyStore, String alias, MemoryUsageSetting memUsageSetting) {
		return load(() -> PDDocument.load(input, password, keyStore, alias, memUsageSetting), 0);
	}

	public Task<PDDocument> load(byte[] input, int initialPageIndex, String password, InputStream keyStore, String alias, MemoryUsageSetting memUsageSetting) {
		return load(() -> PDDocument.load(input, password, keyStore, alias, memUsageSetting), initialPageIndex);
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

	private static Screen getScreen(Node node) {
		if(node == null) {
			return null;
		}

		Point2D pos = node.localToScreen(0.0, 0.0);
		ObservableList<Screen> screens = Screen.getScreensForRectangle(pos.getX(), pos.getY(), 1.0, 1.0);
		if(screens.size() > 0) {
			return screens.get(0);
		}
		return null;
	}
}
