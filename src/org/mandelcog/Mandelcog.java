package org.mandelcog;

import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.JFrame;
import org.mandelcog.ui.Sketch;

/**
 *
 * @author lopho
 */
public class Mandelcog extends ComponentAdapter {

    private final Sketch sketch;
    private final JFrame frame;

    public Mandelcog(int width, int height) {
        sketch = new Sketch(width, height);
        frame = new JFrame("Mandelcog");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(width, height);
        frame.setLocation((Toolkit.getDefaultToolkit().getScreenSize().width - width) / 2, (Toolkit.getDefaultToolkit().getScreenSize().height - height) / 2);
        frame.add(sketch);
        sketch.init();
        frame.addComponentListener(this);
    }

    public void start() {
        frame.setVisible(true);
    }

    @Override
    public void componentResized(ComponentEvent e) {
        sketch.dirty();
    }

}
