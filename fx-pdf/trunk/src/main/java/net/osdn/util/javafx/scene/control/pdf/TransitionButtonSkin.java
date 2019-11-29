package net.osdn.util.javafx.scene.control.pdf;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.skin.ButtonSkin;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.util.Duration;

import java.util.List;

public class TransitionButtonSkin extends ButtonSkin {

	private Duration duration = Duration.millis(66);
	
	private ObjectProperty<Color> textColorProperty
		= new SimpleObjectProperty<Color>(Color.TRANSPARENT);
	
	private ObjectProperty<Color> backgroundColorProperty
		= new SimpleObjectProperty<Color>(Color.TRANSPARENT);
	
	private DoubleProperty opacityProperty
		= new SimpleDoubleProperty(0.0);
	
	private OpaqueBackgroundBinding backgroundBinding
		= new OpaqueBackgroundBinding(backgroundColorProperty, opacityProperty);
	
	private Color textEndColor = getTextColor();
	private Color backgroundEndColor = (Color)getBackgroundFill().getFill();
	
	public TransitionButtonSkin(Button control) {
		super(control);
		
		registerChangeListener(control.focusedProperty(), o -> updateBackground());
		registerChangeListener(control.hoverProperty(), o -> updateBackground());
		registerChangeListener(control.pressedProperty(), o -> updateBackground());
	}
	
	protected Color getTextColor() {
		Paint paint = getSkinnable().getTextFill();
		if(paint instanceof Color) {
			return (Color)paint;
		}
		return Color.TRANSPARENT;
	}
	
	protected BackgroundFill getBackgroundFill() {
		Background background = getSkinnable().getBackground();
		if(background != null) {
			List<BackgroundFill> fills = background.getFills();
			if(fills.size() > 0) {
				Paint paint = fills.get(0).getFill();
				if(paint instanceof Color) {
					return fills.get(0);
				}
			}
		}
		return new BackgroundFill(null, null, null);
	}
	
	protected void updateBackground() {
		Button control = getSkinnable();
		
		control.textFillProperty().unbind();
		control.backgroundProperty().unbind();
		
		control.applyCss();
		
		textColorProperty.setValue(textEndColor);
		backgroundColorProperty.setValue(backgroundEndColor);
		opacityProperty.setValue(backgroundEndColor.getOpacity() == 0.0 ? 0.0 : 1.0);
		
		textEndColor = getTextColor();
		control.textFillProperty().bind(textColorProperty);
		
		BackgroundFill fill = getBackgroundFill();
		backgroundEndColor = (Color)fill.getFill();
		backgroundBinding.radii = fill.getRadii();
		backgroundBinding.insets = fill.getInsets();
		control.backgroundProperty().bind(backgroundBinding);
		
		if(backgroundColorProperty.getValue().getOpacity() > 0.0
				&& backgroundEndColor.getOpacity() > 0.0) {
			new Timeline(
				new KeyFrame(duration,
					new KeyValue(textColorProperty, textEndColor),
					new KeyValue(backgroundColorProperty, backgroundEndColor)
				)
			).play();
		} else if(backgroundColorProperty.getValue().getOpacity() > 0.0) {
			new Timeline(
				new KeyFrame(duration,
					new KeyValue(textColorProperty, textEndColor),
					new KeyValue(opacityProperty, 0.0)
				)
			).play();
		} else if(backgroundEndColor.getOpacity() > 0.0) {
			new Timeline(
				new KeyFrame(Duration.ONE,
					new KeyValue(backgroundColorProperty, backgroundEndColor)
				),
				new KeyFrame(duration,
					new KeyValue(textColorProperty, textEndColor),
					new KeyValue(opacityProperty, 1.0)
				)
			).play();
		} else {
			new Timeline(
				new KeyFrame(duration,
					new KeyValue(textColorProperty, textEndColor)
				)
			).play();
		}
	}
	
	private class OpaqueBackgroundBinding extends ObjectBinding<Background> {
		 
		private ObjectProperty<Color> colorProperty;
		private DoubleProperty opacityProperty;
		private CornerRadii radii;
		private Insets insets;

		public OpaqueBackgroundBinding(ObjectProperty<Color> colorProperty,
				DoubleProperty opacityProperty) {
			this.colorProperty = colorProperty;
			this.opacityProperty = opacityProperty;
			bind(colorProperty, opacityProperty);
		}

		@Override
		protected Background computeValue() {
			Color c = colorProperty.getValue();
			Color color = new Color(
					c.getRed(), c.getGreen(), c.getBlue(),
					c.getOpacity() * opacityProperty.getValue());
			
			return new Background(new BackgroundFill(color, radii, insets));
		}
	}
}
