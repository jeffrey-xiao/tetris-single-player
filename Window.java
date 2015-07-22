import java.awt.*;
public class Window extends Frame {
	/**
	 * 
	 */
	private static final long serialVersionUID = -1324363758675184283L;

	Window () {
		setTitle("Tetris");
		setSize(400, 600);
		setLocation(100, 100);
		setResizable(false);
		add(new Tetris());
		setVisible(true);
	}
}
