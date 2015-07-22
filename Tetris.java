import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;

import javax.imageio.ImageIO;

public class Tetris extends Panel implements KeyListener {


	/**
	 * 
	 */
	private static final long serialVersionUID = -8444879183679955468L;

	// grid of color ids that stores what kind of block is where
	private int[][] grid = new int[22][10];

	// variables for double buffered display
	private BufferedImage bi;
	private Graphics gi;

	// dimensions of the frame
	private Dimension dim;

	// the delay values for levels: the array index corresponds to the level. After level 20 the delay remains consistent
	private final int[] GLOBAL_DELAY = {800,720,630,550,470,380,300,220,130,100,80,80,80,70,70,70,30,30,30,20};

	// the global delay lock value
	private final int GLOBAL_LOCK = 1000;

	// colors representing the different type of blocks
	/*
	 * light gray = empty square
	 * yellow = O
	 * cyan = I
	 * blue = L
	 * orange = J
	 * green = S
	 * red = Z
	 * magenta = T
	 */
	private final Color[] c = {Color.LIGHT_GRAY, Color.YELLOW, Color.CYAN, Color.BLUE, Color.ORANGE, Color.GREEN, Color.RED, Color.MAGENTA};
	private final Color background = Color.BLACK;
	private final Color ghostColor = Color.DARK_GRAY;
	private final Color UIColor = Color.LIGHT_GRAY;

	// Kick cases for J L S T Z blocks
	private final int[][] movec1 = {{0, -1, -1, 0, -1}, 
			{0, +1, +1, 0, +1},
			{0, +1, +1, 0, +1},
			{0, +1, +1, 0, +1},
			{0, +1, +1, 0, +1},
			{0, -1, -1, 0, -1},
			{0, -1, -1, 0, -1},
			{0, -1, -1, 0, -1}};
	private final int[][] mover1 = {{0, 0, +1, 0, -2}, 
			{0, 0, +1, 0, -2},
			{0, 0, -1, 0, +2},
			{0, 0, -1, 0, +2},
			{0, 0, +1, 0, -2},
			{0, 0, +1, 0, -2},
			{0, 0, -1, 0, +2},
			{0, 0, -1, 0, +2}};

	// Kick cases for I block
	private final int[][] movec2 = {{0, -2, +1, -2, +1}, 
			{0, -1, +2, -1, +2},
			{0, -1, +2, -1, +2},
			{0, +2, -1, +2, -1},
			{0, +2, -1, +2, -1},
			{0, +1, -2, +1, -2},
			{0, +1, -2, +1, -2},
			{0, -2, +1, -2, +1}};
	private final int[][] mover2 = {{0, 0, 0, -1, +2}, 
			{0, 0, 0, +2, -1},
			{0, 0, 0, +2, -1},
			{0, 0, 0, +1, -2},
			{0, 0, 0, +1, -2},
			{0, 0, 0, -2, +1},
			{0, 0, 0, -2, +1},
			{0, 0, 0, -1, +2}};

	// Handles the queue for pieces
	private Queue<Integer> bag = new ArrayDeque<Integer>();
	// Generates the pieces
	private Piece p = new Piece();
	// Represents the current active piece
	private Piece.Active curr = null;

	// Variables to manage the hold mechanism
	private int holdId = 0;
	private boolean isHolding = false;

	// Timing and level variables
	private int time = 0;
	private int delay = GLOBAL_DELAY[0];
	private int level = 0;
	private int lockTime = 0;
	private int linesCleared = 0;

	// constants for UI
	private final int[] dy = {50, 100, 150, 200, 300};

	// Game state variables
	private boolean isPaused = false;
	private boolean isGameOver = false;

	// Thread that manages the gravity of the pieces
	private Timer t = new Timer();
	private TimerTask move = new TimerTask() {
		@Override
		public void run () {
			// checking for game states
			if (isPaused || isGameOver)
				return;

			// refill the queue if it is close to empty
			if (bag.size() < 4)
				for (int id : p.getPermutation())
					bag.offer(id);

			if (time >= delay) {
				// getting a new piece
				if (curr == null)
					curr = p.getActive(bag.poll());

				// attempting to move the piece
				if (movePiece(1, 0)) {
					lockTime = 0;
					time = 0;
				} else if (lockTime >= GLOBAL_LOCK) {
					// the piece cannot be moved down any further and the lock delay has expired then place the piece and check for gameover
					isGameOver = true;
					for (int i = 0; i < 4; i++) {
						grid[curr.pos[i].r][curr.pos[i].c] = curr.id;
						if (curr.pos[i].r >= 2)
							isGameOver = false;
					}
					if (isGameOver)
						System.out.println("GAMEOVER -- FINAL SCORE " + linesCleared);

					// set the piece down and allow the user to hold a piece. The lock time is also reset
					synchronized (curr) {
						curr = null;
						isHolding = false;
						lockTime = 0;
					}

					// clear the lines and adjust the level
					clearLines();
					adjustLevel();

					// immediately get another piece
					time = delay;
				}
				repaint();
			}
			time++;
			lockTime++;
		}
	};
	Tetris () {
		addKeyListener(this);
		t.scheduleAtFixedRate(move, 1000, 1);
	}
	// adjust the level based on the number of lines cleared
	private void adjustLevel () {
		level = linesCleared/4;
		if (level >= 20)
			delay = GLOBAL_DELAY[19];
		else
			delay = GLOBAL_DELAY[level];
	}
	public void paint (Graphics g) {
		dim = getSize();
		bi = new BufferedImage(dim.width, dim.height, BufferedImage.TYPE_INT_RGB);
		gi = bi.getGraphics();
		update(g);
	}
	public void update (Graphics g) {
		gi.setColor(background);
		gi.fillRect(0, 0, dim.width, dim.height);
		displayGrid();
		displayPieces();
		displayUI();
		g.drawImage(bi, 0, 0, this);

	}
	// paints the grid based on the color id values in the 2D Array
	private void displayGrid () {
		for (int i = 2; i < 22; i++) {
			for (int j = 0; j < 10; j++) {
				gi.setColor(c[grid[i][j]]);
				gi.fillRect(j*25+10, i*25, 24, 24);
			}
		}
	}
	// paints the current piece
	private void displayPieces () {
		if (curr == null)
			return;
		synchronized (curr) {
			int d = -1;
			// displaying the ghost piece
			boolean isValid = true;
			while (isValid) {
				d++;
				for (Piece.Point block : curr.pos)
					if (block.r+d >= 22 || grid[block.r+d][block.c] != 0)
						isValid = false;
			}
			d--;
			// painting the ghost piece and the active piece
			gi.setColor(ghostColor);
			for (Piece.Point block : curr.pos)
				if (block.r+d >= 2)
					gi.fillRect(block.c*25+10, (block.r+d)*25, 24, 24);

			gi.setColor(c[curr.id]);
			for (Piece.Point block : curr.pos)
				if (block.r >= 2)
					gi.fillRect(block.c*25+10, block.r*25, 24, 24);
		}
	}
	// paints the user interface
	private void displayUI () {
		gi.setColor(UIColor);
		gi.drawString("LINES CLEARED: " + linesCleared, 10, 10);
		gi.drawString("CURRENT LEVEL: " + level, 10, 20);
		if (isPaused)
			gi.drawString("PAUSED", 10, 30);
		if (isGameOver)
			gi.drawString("GAMEOVER -- Q FOR QUIT; R FOR RESTART", 10, 40);
		gi.drawString("HOLD", 300, 300);
		gi.drawString("NEXT", 300, 50);
		for (int k = 0; k < 5; k++) {
			for (int i = 0; i < 2; i++) {
				for (int j = 0; j < 4; j++) {
					gi.fillRect(j*20 + 300, i*20 + dy[k], 19, 19);
				}
			}
		}
		// oaints the hold piece
		if (holdId != 0) {
			Piece.Active holdPiece = p.getActive(holdId-1);
			gi.setColor(c[holdPiece.id]);
			for (Piece.Point block : holdPiece.pos) {
				gi.fillRect((block.c-3)*20+300, block.r*20 + dy[4], 19, 19);
			}
		}
		// paints the queue of blocks
		int i = 0;
		for (int id : bag) {
			Piece.Active nextPiece = p.getActive(id);
			gi.setColor(c[nextPiece.id]);
			for (Piece.Point block : nextPiece.pos) {
				gi.fillRect((block.c-3)*20+300, block.r*20 + dy[i], 19, 19);
			}
			i++;
			if (i >= 4)
				break;
		}
	}
	// Post condition: any full lines are cleared and the respective variable is incremented
	private void clearLines () {
		while (true) {
			// checking if there is a line that is full
			int index = -1;
			for (int j = 0; j < 22; j++) {
				int cnt = 0;
				for (int i = 0; i < 10; i++) {
					cnt += grid[j][i] != 0 ? 1 : 0;
				}
				if (cnt == 10) {
					index = j;
					break;
				}
			}
			if (index == -1)
				break;
			// removing the full lines one by one
			int[][] temp = new int[22][10];
			for (int i = 0; i < 22; i++)
				for (int j = 0; j < 10; j++)
					temp[i][j] = grid[i][j];
			for (int i = 0; i < index+1; i++) {
				for (int j = 0; j < 10; j++) {
					if (i == 0)
						grid[i][j] = 0;
					else
						grid[i][j] = temp[i-1][j];
				}
			}
			linesCleared++;
		}
	}

	@Override
	public void keyTyped (KeyEvent e) {
	}
	@Override
	public void keyReleased (KeyEvent e) {
		// when down is released, soft drop is deactivated
		if (e.getKeyCode() == KeyEvent.VK_DOWN)
			delay = level >= 20 ? GLOBAL_DELAY[19] : GLOBAL_DELAY[level];
	}
	@Override
	public void keyPressed (KeyEvent e) {
		// user input

		// three cases that handle when the user adjusts the game states (ACTIVE, PAUSED, CLOSEd)
		if (e.getKeyCode() == KeyEvent.VK_P) {
			isPaused = !isPaused;
			repaint();
		} else if (e.getKeyCode() == KeyEvent.VK_Q) {
			System.exit(0);
		} else if (e.getKeyCode() == KeyEvent.VK_R) {
			curr = null;
			grid = new int[22][10];
			bag.clear();
			level = 0;
			linesCleared = 0;
			holdId = 0;
			isHolding = false;
			isGameOver = false;
			repaint();
			return;
		}
		if (curr == null || isPaused)
			return;
		switch (e.getKeyCode()) {
			// Move piece left
			case KeyEvent.VK_LEFT:
				movePiece(0, -1);
				repaint();
				break;
				// Move piece right
			case KeyEvent.VK_RIGHT:
				movePiece(0, 1);
				repaint();
				break;
				// rotate clockwise
			case KeyEvent.VK_UP:
			case KeyEvent.VK_X:
				rotateRight();
				break;	
				// rotate counterclockwise
			case KeyEvent.VK_Z:
				rotateLeft();
				break;
				// soft drop
			case KeyEvent.VK_DOWN:
				delay = (level >= 20 ? GLOBAL_DELAY[19] : GLOBAL_DELAY[level])/8;
				break;
				// hold piece
			case KeyEvent.VK_SHIFT:
			case KeyEvent.VK_C:
				if (isHolding)
					break;
				if (holdId == 0) {
					holdId = curr.id;
					curr = null;
				} else {
					int temp = holdId;
					holdId = curr.id;
					curr = p.getActive(temp-1);
				}
				isHolding = true;
				time = 1 << 30;
				break;
			// hard drop
			case KeyEvent.VK_SPACE:
				time = 1 << 30;
				lockTime = 1 << 30;
			// firm drop
			case KeyEvent.VK_CONTROL:
				time = 1 << 30;
				while(movePiece(1, 0));
				break;
		}
		repaint();
	}
	// attempt to rotate the piece counterclockwise
	// Post condition: the current piece will be rotated counterclockwise if there is one case (out of five) that work
	private void rotateLeft () {
		if (curr.id == 1)
			return;
		Piece.Point[] np = new Piece.Point[4];
		for (int i = 0; i < 4; i++) {
			int nr = curr.pos[i].c - curr.loc + curr.lor;
			int nc = curr.pos[i].r - curr.lor + curr.loc;
			np[i] = new Piece.Point(nr, nc);
		}
		int lor = curr.lor;
		int hir = curr.hir;
		for (int i = 0; i < 4; i++) {
			np[i].r= hir - (np[i].r-lor);
		}
		kick(np, curr.state*2+1);
		repaint();
	}
	// attempt to rotate the piece clockwise
	// Post condition: the current piece will be rotated clockwise if there is one case (out of five) that work
	private void rotateRight () {
		if (curr.id == 1)
			return;
		Piece.Point[] np = new Piece.Point[4];
		for (int i = 0; i < 4; i++) {
			int nr = curr.pos[i].c - curr.loc + curr.lor;
			int nc = curr.pos[i].r - curr.lor + curr.loc;
			np[i] = new Piece.Point(nr, nc);
		}
		int loc = curr.loc;
		int hic = curr.hic;
		for (int i = 0; i < 4; i++) {
			np[i].c = hic - (np[i].c-loc);
		}
		kick(np, curr.state*2);
		repaint();

	}
	// handles the kick cases
	// Post condition: rotates the piece according to the state of the rotation
	// this method performs the actual rotation and copies the positions of the blocks into the active block
	private void kick (Piece.Point[] pos, int id) {
		for (int i = 0; i < 5; i++) {
			boolean valid = true;
			int dr = curr.id == 2 ? mover2[id][i] : mover1[id][i];
			int dc = curr.id == 2 ? movec2[id][i] : movec1[id][i];
			for (Piece.Point block : pos) {
				if (block.r + dr < 0 || block.r + dr >= 22)
					valid = false;
				else if (block.c + dc < 0 || block.c + dc >= 10)
					valid = false;
				else if (grid[block.r+dr][block.c+dc] != 0)
					valid = false;
			}
			if (valid) {
				for (int j = 0; j < 4; j++) {
					curr.pos[j].r = pos[j].r + dr;
					curr.pos[j].c = pos[j].c + dc;
				}
				curr.hic += dc;
				curr.loc += dc;
				curr.hir += dr;
				curr.lor += dr;
				if (id % 2 == 1)
					curr.state = (curr.state+3)%4;
				else
					curr.state = (curr.state+1)%4;
				return;
			}
		}
	}
	// attempts to move the active piece
	// Post-condition: will return false if it cannot move and true if it can move
	private boolean movePiece (int dr, int dc) {
		if (curr == null)
			return false;
		for (Piece.Point block : curr.pos) {
			if (block.r+dr >= 22)
				return false;
			if (block.c+dc < 0 || block.c+dc >= 10)
				return false;
			if (grid[block.r+dr][block.c+dc] != 0)
				return false;
		}
		for (int i = 0; i < 4; i++) {
			curr.pos[i].r += dr;
			curr.pos[i].c += dc;
		}
		curr.loc += dc;
		curr.hic += dc;
		curr.lor += dr;
		curr.hir += dr;
		return true;
	}
}
