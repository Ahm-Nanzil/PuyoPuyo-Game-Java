import java.io.ByteArrayInputStream;
import java.util.Random;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Modern implementation of Puyo Puyo game using Java Swing
 * with improved graphics, sound handling, and code organization
 */
public class PuyoPuyo extends JFrame {
    private GamePanel gamePanel;
    private final int COLS = 6;
    private final int ROWS;
    private final int PUYO_SIZE;
    private final Dimension screenSize;

    /**
     * Constructor initializes the game window and components
     */
    public PuyoPuyo() {
        super("Puyo Puyo");

        // Calculate screen size and optimal game dimensions
        screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int width = screenSize.width;
        int height = screenSize.height;

        ROWS = COLS * 2;
        PUYO_SIZE = Math.min((width / 8) * 2 / COLS, (height / 6) * 4 / (ROWS + 1));

        // Initialize the game panel
        gamePanel = new GamePanel(PUYO_SIZE, ROWS, COLS);

        // Set up the frame
        setContentPane(gamePanel);
        setResizable(false);

        // Place window in the center of the screen with appropriate size
        int windowWidth = PUYO_SIZE * COLS + PUYO_SIZE * 3 + 6;
        int windowHeight = PUYO_SIZE * (ROWS + 1) + 25;
        setBounds(
                (width - windowWidth) / 2,
                (height - windowHeight) / 4,
                windowWidth,
                windowHeight
        );

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
    }

    /**
     * Main method to start the game
     */
    public static void main(String[] args) {
        System.out.println("Starting Puyo Puyo...");

        // Use Java's invokeLater for Swing applications
        SwingUtilities.invokeLater(() -> {
            try {
                // Set system look and feel for better integration
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new PuyoPuyo();
        });
    }
}

/**
 * Panel class handling the game logic and rendering
 */
class GamePanel extends JPanel implements ActionListener {
    // Game constants
    private static final int MAX_LEVEL = 20;
    private static final int MIN_LEVEL = 15;

    // Game grid and state
    private final int rows;
    private final int cols;
    private int[][] grid;
    private final int puyoSize;

    // Game state variables
    private int rotation;
    private boolean puyoLanded;
    private boolean gameStarted;
    private boolean gameOver;
    private boolean gamePaused;
    private int nextPuyoA;
    private int nextPuyoB;
    private int level;
    private int score;
    private int piecesPlaced;
    private int removedPuyos;
    private int minScore;
    private int animationOffset;
    private float fadeAlpha;
    private float pauseAlpha;
    private boolean increasingLevel;

    // Tetris formation checking
    private Node tetrisChain;
    private int chainLength;

    // Resources
    private final BufferedImage[] puyoImages = new BufferedImage[4];
    private BufferedImage frontPipe;
    private BufferedImage backPipe;
    private final Random random = new Random();
    private final SoundManager soundManager;

    // Timers
    private final Timer gameTimer;
    private final Timer eraseTimer;
    private final Timer fallTimer;
    private final Timer animationTimer;

    // Thread pool for background operations
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    /**
     * Constructor initializes the game panel and resources
     */
    public GamePanel(int puyoSize, int rows, int cols) {
        this.puyoSize = puyoSize;
        this.rows = rows + 1;  // Add one row for the pipe
        this.cols = cols;

        // Initialize sound manager
        soundManager = new SoundManager();

        // Initialize game state
        initializeGame();

        // Create timers with appropriate delays
        gameTimer = new Timer(1000, this);
        gameTimer.setInitialDelay(0);

        eraseTimer = new Timer(1000, this);
        fallTimer = new Timer(500, this);

        animationTimer = new Timer(50, this);
        animationTimer.start();

        // Load images and sounds
        executor.execute(this::loadResources);

        // Generate initial puyos
        generatePuyos();

        // Play background music
        soundManager.playBackgroundMusic();

        // Set up keyboard controls
        setupKeyBindings();

        setFocusable(true);
        requestFocusInWindow();
    }

    /**
     * Initialize all game state variables
     */
    private void initializeGame() {
        grid = new int[rows][cols];
        rotation = 1;
        puyoLanded = true;
        chainLength = 0;
        gameStarted = false;
        gameOver = false;
        gamePaused = false;
        nextPuyoA = 0;
        nextPuyoB = 0;
        level = 0;
        score = 0;
        piecesPlaced = -1;
        removedPuyos = 0;
        minScore = 50;
        animationOffset = 0;
        fadeAlpha = 0.0f;
        pauseAlpha = 0.0f;
        increasingLevel = true;
    }

    /**
     * Load game resources (images and sounds)
     */
    private void loadResources() {
        try {
            // Load puyo images based on size
            String sizeSuffix = puyoSize >= 42 ? "_" : "";
            for (int i = 0; i < puyoImages.length; i++) {
                String filename = "images/puyo_" + sizeSuffix + (i + 1) + ".png";
                try {
                    puyoImages[i] = ImageIO.read(new File(filename));
                } catch (IOException e) {
                    // Create fallback image if file not found
                    puyoImages[i] = createFallbackPuyoImage(i);
                }
            }

            // Load pipe images
            try {
                frontPipe = ImageIO.read(new File("images/pipe" + sizeSuffix + "1.png"));
                backPipe = ImageIO.read(new File("images/pipe" + sizeSuffix + ".png"));
            } catch (IOException e) {
                // Create fallback pipe images
                frontPipe = createFallbackPipeImage(true);
                backPipe = createFallbackPipeImage(false);
            }

            // Initialize sounds
            soundManager.loadSounds();

        } catch (Exception e) {
            System.err.println("Error loading resources: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Create a fallback puyo image if the file cannot be loaded
     */
    private BufferedImage createFallbackPuyoImage(int colorIndex) {
        Color[] colors = {Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW};
        BufferedImage img = new BufferedImage(puyoSize, puyoSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(colors[colorIndex]);
        g2d.fillOval(2, 2, puyoSize - 4, puyoSize - 4);
        g2d.setColor(Color.WHITE);
        g2d.fillOval(puyoSize/4, puyoSize/4, puyoSize/5, puyoSize/5);
        g2d.setColor(Color.BLACK);
        g2d.drawOval(0, 0, puyoSize - 1, puyoSize - 1);

        g2d.dispose();
        return img;
    }

    /**
     * Create a fallback pipe image if the file cannot be loaded
     */
    private BufferedImage createFallbackPipeImage(boolean isFront) {
        BufferedImage img = new BufferedImage(puyoSize, puyoSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (isFront) {
            g2d.setColor(new Color(120, 120, 120));
            g2d.fillRect(0, 0, puyoSize, puyoSize/2);
            g2d.setColor(Color.GRAY);
            g2d.fillRect(0, puyoSize/2, puyoSize, puyoSize/2);
        } else {
            g2d.setColor(Color.DARK_GRAY);
            g2d.fillRect(0, 0, puyoSize, puyoSize);
            g2d.setColor(Color.BLACK);
            g2d.drawRect(0, 0, puyoSize-1, puyoSize-1);
        }

        g2d.dispose();
        return img;
    }

    /**
     * Set up keyboard controls
     */
    private void setupKeyBindings() {
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                processKeyInput(e.getKeyCode());
            }
        });
    }

    /**
     * Process keyboard input
     */
    private void processKeyInput(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_ENTER:
                handleEnterKey();
                break;
            case KeyEvent.VK_LEFT:
                if (!puyoLanded && !gamePaused) {
                    soundManager.playSound(SoundManager.MOVE_SOUND);
                    moveLeft();
                }
                break;
            case KeyEvent.VK_RIGHT:
                if (!puyoLanded && !gamePaused) {
                    soundManager.playSound(SoundManager.MOVE_SOUND);
                    moveRight();
                }
                break;
            case KeyEvent.VK_UP:
                soundManager.playSound(SoundManager.MOVE_SOUND);
                if (!puyoLanded && !gamePaused) {
                    rotate();
                } else if (!gameStarted && level < MAX_LEVEL) {
                    level++;
                }
                break;
            case KeyEvent.VK_DOWN:
                soundManager.playSound(SoundManager.MOVE_SOUND);
                if (!gamePaused) {
                    moveDown();
                } else if (!gameStarted && level > 0) {
                    level--;
                }
                break;
            case KeyEvent.VK_P:
                handlePauseKey();
                break;
            case KeyEvent.VK_ESCAPE:
                handleEscapeKey();
                break;
        }
        repaint();
    }

    /**
     * Handle Enter key press
     */
    private void handleEnterKey() {
        if (!gameStarted) {
            soundManager.stopBackgroundMusic();
            soundManager.playSound(SoundManager.START_SOUND);
            updateTimerDelays();
            gameTimer.start();
            gameStarted = true;
        } else if (gameOver || gamePaused) {
            initializeGame();
            generatePuyos();
            soundManager.playBackgroundMusic();
            gameStarted = false;
        }
    }

    /**
     * Handle Pause key press
     */
    private void handlePauseKey() {
        if (gameStarted && !gameOver) {
            if (gamePaused) {
                soundManager.playSound(SoundManager.START_SOUND);
                gamePaused = false;
                pauseAlpha = 0.0f;
                gameTimer.start();
            } else {
                soundManager.playSound(SoundManager.PAUSE_SOUND);
                gameTimer.stop();
                gamePaused = true;
            }
        }
    }

    /**
     * Handle Escape key press
     */
    private void handleEscapeKey() {
        soundManager.playSound(SoundManager.PAUSE_SOUND);
        if (gameStarted && !gameOver) {
            if (gamePaused) {
                cleanup();
                System.exit(0);
            } else {
                gameTimer.stop();
                gamePaused = true;
            }
        } else {
            cleanup();
            System.exit(0);
        }
    }

    /**
     * Clean up resources before exiting
     */
    private void cleanup() {
        soundManager.stopAll();
        executor.shutdown();
    }

    /**
     * Update timer delays based on current level
     */
    private void updateTimerDelays() {
        int delay = 0;
        int animationDelay = 0;

        for (int i = 0; i <= level; i++) {
            delay += 20 * (4 - i / 5);
            animationDelay += 4 - i / 5;
        }

        if (level == MAX_LEVEL) {
            delay += 25;
            animationDelay += 1;
        }

        gameTimer.setDelay(Math.max(100, 1075 - delay));
        animationTimer.setDelay(Math.max(10, 52 - animationDelay));
        animationTimer.restart();
    }

    /**
     * Generate new puyos at the top
     */
    private void generatePuyos() {
        int pipePosition = cols % 2 == 0 ? cols / 2 - 1 : cols / 2;

        // Check if position is already occupied (game over)
        if (grid[0][pipePosition] == 0 && grid[1][pipePosition] == 0) {
            soundManager.playSound(SoundManager.DROP_SOUND);
            grid[0][pipePosition] = nextPuyoA;
            grid[1][pipePosition] = nextPuyoB;
        } else {
            soundManager.playSound(SoundManager.GAME_OVER_SOUND);
            gameTimer.stop();
            gameOver = true;
            return;
        }

        // Generate new random puyos for next turn
        // Odd numbers (1,3,5,7) for moving puyos, even numbers (2,4,6,8) for stationary puyos
        nextPuyoA = random.nextInt(4) * 2 + 1;
        nextPuyoB = random.nextInt(4) * 2 + 1;

        piecesPlaced++;
        rotation = 1;
    }

    /**
     * Handle timer events
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();

        if (source == gameTimer) {
            soundManager.playSound(SoundManager.TICK_SOUND);
            movePuyosDown();
        } else if (source == eraseTimer) {
            soundManager.playSound(SoundManager.COMBO_SOUND);
            erasePuyos();
        } else if (source == fallTimer) {
            soundManager.playSound(SoundManager.FALL_SOUND);
            fillVacatedSpaces();
            fallTimer.stop();
        } else if (source == animationTimer) {
            // This timer handles all animations updates
            repaint();
        }
    }

    /**
     * Move active puyos downward
     */
    private void movePuyosDown() {
        boolean anyMoved = false;

        // Process from bottom to top to avoid moving the same puyo multiple times
        for (int i = rows - 1; i >= 0; i--) {
            for (int j = 0; j < cols; j++) {
                if (grid[i][j] % 2 == 1) {  // Moving puyo (odd number)
                    if (i == rows - 1) {  // Bottom row
                        grid[i][j] += 1;  // Convert to stationary puyo
                        puyoLanded = true;
                    } else if (grid[i + 1][j] == 0) {  // Empty space below
                        grid[i + 1][j] = grid[i][j];
                        grid[i][j] = 0;
                        anyMoved = true;
                    } else {  // Space below is occupied
                        grid[i][j] += 1;  // Convert to stationary puyo
                        puyoLanded = true;
                    }
                    animationOffset = 0;  // Reset animation for smooth motion
                }
            }
        }

        if (!anyMoved) {
            checkForTetris();
        }
    }

    /**
     * Check for and handle tetris formations
     */
    private void checkForTetris() {
        boolean tetrisFound = false;

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (grid[i][j] > 0) {  // For all color puyos
                    chainLength = 1;
                    tetrisChain = new Node(i, j);
                    findConnectedPuyos(i, j);

                    if (chainLength >= 4) {  // If tetris forms
                        removeAllTetrisPuyos();
                        tetrisFound = true;
                    }
                }
            }
        }

        if (tetrisFound) {
            gameTimer.stop();
            eraseTimer.start();
            fallTimer.start();
            return;
        }

        // If no tetris found, reset and generate new puyos
        eraseTimer.stop();
        minScore = 50;
        generatePuyos();

        if (!gameTimer.isRunning()) {
            gameTimer.start();
        }
    }

    /**
     * Find all connected puyos of the same color recursively
     */
    private void findConnectedPuyos(int x, int y) {
        int currentColor = grid[x][y];

        // Check right
        if (y < cols - 1 && grid[x][y + 1] == currentColor && !isInTetrisChain(x, y + 1)) {
            chainLength++;
            addToTetrisChain(x, y + 1);
            findConnectedPuyos(x, y + 1);
        }

        // Check down
        if (x < rows - 1 && grid[x + 1][y] == currentColor && !isInTetrisChain(x + 1, y)) {
            chainLength++;
            addToTetrisChain(x + 1, y);
            findConnectedPuyos(x + 1, y);
        }

        // Check left
        if (y > 0 && grid[x][y - 1] == currentColor && !isInTetrisChain(x, y - 1)) {
            chainLength++;
            addToTetrisChain(x, y - 1);
            findConnectedPuyos(x, y - 1);
        }

        // Check up
        if (x > 0 && grid[x - 1][y] == currentColor && !isInTetrisChain(x - 1, y)) {
            chainLength++;
            addToTetrisChain(x - 1, y);
            findConnectedPuyos(x - 1, y);
        }
    }

    /**
     * Add a node to the tetris chain
     */
    private void addToTetrisChain(int x, int y) {
        tetrisChain.setNext(new Node(x, y));
        tetrisChain.getNext().setPrev(tetrisChain);
        tetrisChain = tetrisChain.getNext();
    }

    /**
     * Check if a position is already in the tetris chain
     */
    private boolean isInTetrisChain(int x, int y) {
        Node current = tetrisChain;
        while (current != null) {
            if (current.getX() == x && current.getY() == y) {
                return true;
            }
            current = current.getPrev();
        }
        return false;
    }

    /**
     * Remove all puyos in the tetris chain
     */
    private void removeAllTetrisPuyos() {
        Node current = tetrisChain;
        while (current != null) {
            grid[current.getX()][current.getY()] = 0;
            current = current.getPrev();
        }

        removedPuyos += chainLength;
        updateLevelAndScore();
    }

    /**
     * Update level and score based on removed puyos
     */
    private void updateLevelAndScore() {
        if (removedPuyos >= 50) {
            if (increasingLevel) {
                level += 1;
            } else {
                level -= 1;
            }

            // Handle level direction change
            if (level == MAX_LEVEL) {
                increasingLevel = false;
            } else if (level == MIN_LEVEL && !increasingLevel) {
                increasingLevel = true;
            }

            updateTimerDelays();
            removedPuyos = 0;
        }

        // Score calculation based on chain length and previous chain's score
        score += minScore * (chainLength - 3) * chainLength;
        minScore = minScore * chainLength;
    }

    /**
     * Fill spaces vacated by removed puyos
     */
    private void fillVacatedSpaces() {
        for (int j = 0; j < cols; j++) {
            // For each column, move puyos down
            int emptyRow = rows - 1;
            for (int i = rows - 1; i >= 0; i--) {
                if (grid[i][j] > 0) {  // Found a puyo
                    if (i != emptyRow) {  // If not already at lowest position
                        grid[emptyRow][j] = grid[i][j];
                        grid[i][j] = 0;
                    }
                    emptyRow--;  // Next empty row for this column
                }
            }
        }

        // Check for new tetris formations after falling
        checkForTetris();
    }

    /**
     * Move active puyos to the left
     */
    private void moveLeft() {
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (grid[i][j] > 0 && grid[i][j] % 2 == 1 && j > 0) {
                    // Horizontal puyos
                    if (j < cols - 1 && grid[i][j + 1] % 2 == 1 && grid[i][j - 1] == 0) {
                        grid[i][j - 1] = grid[i][j];
                        grid[i][j] = grid[i][j + 1];
                        grid[i][j + 1] = 0;
                        return;
                    }
                    // Vertical puyos
                    else if (i < rows - 1 && grid[i + 1][j] % 2 == 1 &&
                            grid[i][j - 1] == 0 && grid[i + 1][j - 1] == 0) {
                        grid[i][j - 1] = grid[i][j];
                        grid[i + 1][j - 1] = grid[i + 1][j];
                        grid[i][j] = 0;
                        grid[i + 1][j] = 0;
                        return;
                    }
                }
            }
        }
    }

    /**
     * Move active puyos to the right
     */
    private void moveRight() {
        for (int i = 0; i < rows; i++) {
            for (int j = cols - 1; j >= 0; j--) {
                if (grid[i][j] > 0 && grid[i][j] % 2 == 1 && j < cols - 1) {
                    // Horizontal puyos
                    if (j > 0 && grid[i][j - 1] % 2 == 1 && grid[i][j + 1] == 0) {
                        grid[i][j + 1] = grid[i][j];
                        grid[i][j] = grid[i][j - 1];
                        grid[i][j - 1] = 0;
                        return;
                    }
                    // Vertical puyos
                    else if (i < rows - 1 && grid[i + 1][j] % 2 == 1 &&
                            grid[i][j + 1] == 0 && grid[i + 1][j + 1] == 0) {
                        grid[i][j + 1] = grid[i][j];
                        grid[i + 1][j + 1] = grid[i + 1][j];
                        grid[i][j] = 0;
                        grid[i + 1][j] = 0;
                        return;
                    }
                }
            }
        }
    }

    /**
     * Rotate active puyos clockwise
     */
    private void rotate() {
        // Find the active puyo pair
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (grid[i][j] > 0 && grid[i][j] % 2 == 1) {
                    // Handle rotation based on current orientation
                    switch (rotation) {
                        case 1:  // Vertical: bottom puyo moves left
                            if (j > 0 && grid[i][j - 1] == 0) {
                                grid[i][j - 1] = grid[i + 1][j];
                                grid[i + 1][j] = 0;
                                rotation = 2;
                            } else if (j < cols - 1 && grid[i][j + 1] == 0) {
                                // If left is blocked, try rotating by moving right
                                grid[i][j + 1] = grid[i][j];
                                grid[i][j] = grid[i + 1][j];
                                grid[i + 1][j] = 0;
                                rotation = 2;
                            }
                            break;
                        case 2:  // Horizontal: right puyo moves up
                            if (i > 1 && grid[i - 1][j + 1] == 0) {
                                grid[i - 1][j + 1] = grid[i][j];
                                grid[i][j] = 0;
                                rotation = 3;
                            }
                            break;
                        case 3:  // Inverted vertical: top puyo moves right
                            if (j < cols - 1 && grid[i + 1][j + 1] == 0) {
                                grid[i + 1][j + 1] = grid[i][j];
                                grid[i][j] = 0;
                                rotation = 4;
                            } else if (j > 0 && grid[i + 1][j - 1] == 0) {
                                // If right is blocked, try rotating by moving left
                                grid[i + 1][j - 1] = grid[i + 1][j];
                                grid[i + 1][j] = grid[i][j];
                                grid[i][j] = 0;
                                rotation = 4;
                            }
                            break;
                        case 4:  // Inverted horizontal: left puyo moves down
                            if (i < rows - 1 && grid[i + 1][j] == 0) {
                                grid[i + 1][j] = grid[i][j + 1];
                                grid[i][j + 1] = 0;
                                rotation = 1;
                            }
                            break;
                    }
                    return;
                }
            }
        }
    }

    /**
     * Move active puyos down one step (when down arrow is pressed)
     */
    private void moveDown() {
        for (int i = rows - 1; i >= 0; i--) {
            for (int j = 0; j < cols; j++) {
                if (grid[i][j] % 2 == 1) {  // Active puyo
                    if (i == rows - 1) {  // Bottom row
                        grid[i][j] += 1;  // Convert to stationary
                        puyoLanded = true;
                    } else if (grid[i + 1][j] > 0 && grid[i + 1][j] % 2 == 0) {
                        // Puyo below is stationary
                        grid[i][j] += 1;  // Convert to stationary
                        puyoLanded = true;
                    } else {
                        grid[i + 1][j] = grid[i][j];  // Move down
                        grid[i][j] = 0;
                    }
                }
            }
        }
    }

    /**
     * Paint the game components
     */
    /**
     * Paint the game components
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        // Set rendering hints for better quality
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Draw background
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, puyoSize * cols, puyoSize * rows);

        // Draw grid background
        g2d.setColor(new Color(240, 240, 240));
        g2d.fillRect(0, 0, puyoSize * cols, puyoSize * rows);

        // Draw side panel background
        g2d.setColor(new Color(220, 220, 220));
        g2d.fillRect(puyoSize * cols, 0, puyoSize * 3, puyoSize * rows);

        // Draw grid lines
        g2d.setColor(new Color(200, 200, 200));
        for (int i = 0; i <= rows; i++) {
            g2d.drawLine(0, i * puyoSize, puyoSize * cols, i * puyoSize);
        }
        for (int j = 0; j <= cols; j++) {
            g2d.drawLine(j * puyoSize, 0, j * puyoSize, puyoSize * rows);
        }

        // Draw puyos
        drawPuyos(g2d);

        // Draw pipe
        drawPipe(g2d);

        // Draw next puyos
        drawNextPuyos(g2d);

        // Draw game info
        drawGameInfo(g2d);

        // Draw game state overlays
        if (!gameStarted) {
            drawStartScreen(g2d);
        } else if (gameOver) {
            drawGameOverScreen(g2d);
        } else if (gamePaused) {
            drawPauseScreen(g2d);
        }

        // Animate puyos falling
        if (!puyoLanded && gameStarted && !gamePaused) {
            animationOffset += 5;
            if (animationOffset >= puyoSize) {
                animationOffset = 0;
            }
        }
    }

    /**
     * Draw all puyos on the grid
     */
    private void drawPuyos(Graphics2D g2d) {
        // Draw stationary puyos
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                int puyo = grid[i][j];
                if (puyo > 0) {
                    // Get color index (1-8 maps to 0-3)
                    int colorIndex = (puyo - 1) / 2;

                    // Calculate position
                    int yOffset = puyo % 2 == 1 ? animationOffset : 0;
                    int x = j * puyoSize;
                    int y = (i * puyoSize) - yOffset;

                    g2d.drawImage(puyoImages[colorIndex], x, y, null);
                }
            }
        }
    }

    /**
     * Draw the pipe at the top of the game
     */
    private void drawPipe(Graphics2D g2d) {
        // Draw pipe in the middle top of the game field
        int pipeCol = cols % 2 == 0 ? cols / 2 - 1 : cols / 2;
        g2d.drawImage(backPipe, pipeCol * puyoSize, 0, null);
        g2d.drawImage(frontPipe, pipeCol * puyoSize, 0, null);
    }

    /**
     * Draw the next puyos preview
     */
    private void drawNextPuyos(Graphics2D g2d) {
        if (nextPuyoA > 0 && nextPuyoB > 0) {
            // Draw "Next" label
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.BOLD, puyoSize / 2));
            g2d.drawString("NEXT:", puyoSize * cols + 20, puyoSize * 2);

            // Calculate color indices
            int colorA = (nextPuyoA - 1) / 2;
            int colorB = (nextPuyoB - 1) / 2;

            // Draw next puyos
            g2d.drawImage(puyoImages[colorA], puyoSize * cols + 20, puyoSize * 3, null);
            g2d.drawImage(puyoImages[colorB], puyoSize * cols + 20, puyoSize * 4, null);
        }
    }

    /**
     * Draw game information (score, level, etc.)
     */
    private void drawGameInfo(Graphics2D g2d) {
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, puyoSize / 2));

        // Draw score
        g2d.drawString("SCORE:", puyoSize * cols + 20, puyoSize * 6);
        g2d.drawString(String.valueOf(score), puyoSize * cols + 20, puyoSize * 7 - 10);

        // Draw level
        g2d.drawString("LEVEL:", puyoSize * cols + 20, puyoSize * 8);
        g2d.setColor(level < 5 ? Color.GREEN : level < 10 ? Color.ORANGE : Color.RED);
        g2d.drawString(String.valueOf(level), puyoSize * cols + 20, puyoSize * 9 - 10);

        // Draw pieces placed
        g2d.setColor(Color.BLACK);
        g2d.drawString("PIECES:", puyoSize * cols + 20, puyoSize * 10);
        g2d.drawString(String.valueOf(piecesPlaced), puyoSize * cols + 20, puyoSize * 11 - 10);
    }

    /**
     * Draw the start screen
     */
    private void drawStartScreen(Graphics2D g2d) {
        // Create semi-transparent overlay
        g2d.setColor(new Color(0, 0, 0, 150));
        g2d.fillRect(0, 0, getWidth(), getHeight());

        // Draw title
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, puyoSize));
        String title = "PUYO PUYO";
        int titleWidth = g2d.getFontMetrics().stringWidth(title);
        g2d.drawString(title, (getWidth() - titleWidth) / 2, puyoSize * 4);

        // Draw instructions
        g2d.setFont(new Font("Arial", Font.PLAIN, puyoSize / 2));
        drawCenteredString(g2d, "↑↓ Level: " + level, puyoSize * 6);
        drawCenteredString(g2d, "Press ENTER to start", puyoSize * 7);
        drawCenteredString(g2d, "←→↑↓ Move and Rotate", puyoSize * 8);
        drawCenteredString(g2d, "P: Pause, ESC: Quit", puyoSize * 9);
    }

    /**
     * Draw the game over screen
     */
    private void drawGameOverScreen(Graphics2D g2d) {
        // Create semi-transparent overlay with alpha animation
        fadeAlpha = Math.min(0.8f, fadeAlpha + 0.02f);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fadeAlpha));
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, getWidth(), getHeight());
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        // Draw game over text
        g2d.setColor(Color.RED);
        g2d.setFont(new Font("Arial", Font.BOLD, puyoSize));
        String gameOverText = "GAME OVER";
        int textWidth = g2d.getFontMetrics().stringWidth(gameOverText);
        g2d.drawString(gameOverText, (getWidth() - textWidth) / 2, puyoSize * 5);

        // Draw final score
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, puyoSize / 2));
        drawCenteredString(g2d, "Final Score: " + score, puyoSize * 7);
        drawCenteredString(g2d, "Press ENTER to restart", puyoSize * 9);
    }

    /**
     * Draw the pause screen
     */
    private void drawPauseScreen(Graphics2D g2d) {
        // Create semi-transparent overlay with alpha animation
        pauseAlpha = Math.min(0.7f, pauseAlpha + 0.05f);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pauseAlpha));
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, getWidth(), getHeight());
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        // Draw pause text
        g2d.setColor(Color.YELLOW);
        g2d.setFont(new Font("Arial", Font.BOLD, puyoSize));
        String pauseText = "PAUSED";
        int textWidth = g2d.getFontMetrics().stringWidth(pauseText);
        g2d.drawString(pauseText, (getWidth() - textWidth) / 2, puyoSize * 5);

        // Draw instructions
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.PLAIN, puyoSize / 2));
        drawCenteredString(g2d, "Press P to resume", puyoSize * 7);
        drawCenteredString(g2d, "Press ENTER to restart", puyoSize * 8);
        drawCenteredString(g2d, "Press ESC to quit", puyoSize * 9);
    }

    /**
     * Draw a string centered horizontally on the screen
     */
    private void drawCenteredString(Graphics2D g2d, String text, int y) {
        int textWidth = g2d.getFontMetrics().stringWidth(text);
        g2d.drawString(text, (getWidth() - textWidth) / 2, y);
    }

    /**
     * Erase puyos marked for removal
     */
    private void erasePuyos() {
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (grid[i][j] == -1) {
                    grid[i][j] = 0;
                }
            }
        }
        eraseTimer.stop();
    }
}

/**
 * Node class for tetris chain linked list
 */
class Node {
    private int x;
    private int y;
    private Node next;
    private Node prev;

    /**
     * Create a new node with coordinates
     */
    public Node(int x, int y) {
        this.x = x;
        this.y = y;
        this.next = null;
        this.prev = null;
    }

    /**
     * Get the x coordinate
     */
    public int getX() {
        return x;
    }

    /**
     * Get the y coordinate
     */
    public int getY() {
        return y;
    }

    /**
     * Set the next node
     */
    public void setNext(Node next) {
        this.next = next;
    }

    /**
     * Get the next node
     */
    public Node getNext() {
        return next;
    }

    /**
     * Set the previous node
     */
    public void setPrev(Node prev) {
        this.prev = prev;
    }

    /**
     * Get the previous node
     */
    public Node getPrev() {
        return prev;
    }
}

/**
 * Sound manager to handle all game audio
 */
class SoundManager {
    // Sound indices
    public static final int MOVE_SOUND = 0;
    public static final int DROP_SOUND = 1;
    public static final int FALL_SOUND = 2;
    public static final int COMBO_SOUND = 3;
    public static final int TICK_SOUND = 4;
    public static final int START_SOUND = 5;
    public static final int PAUSE_SOUND = 6;
    public static final int GAME_OVER_SOUND = 7;

    // Sound clips
    private Clip[] soundClips;
    private Clip backgroundMusic;
    private boolean soundEnabled = true;

    /**
     * Constructor initializes sound arrays
     */
    public SoundManager() {
        soundClips = new Clip[8];
    }

    /**
     * Load all sound files
     */
    public void loadSounds() {
        try {
            // Load effect sounds
            String[] soundFiles = {
                    "sounds/move.wav",
                    "sounds/drop.wav",
                    "sounds/fall.wav",
                    "sounds/combo.wav",
                    "sounds/tick.wav",
                    "sounds/start.wav",
                    "sounds/pause.wav",
                    "sounds/gameover.wav"
            };

            for (int i = 0; i < soundFiles.length; i++) {
                try {
                    AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new File(soundFiles[i]));
                    soundClips[i] = AudioSystem.getClip();
                    soundClips[i].open(audioInputStream);
                } catch (Exception e) {
                    // Create fallback sounds if file not found
                    soundClips[i] = createFallbackSound(i);
                }
            }

            // Load background music
            try {
                AudioInputStream musicStream = AudioSystem.getAudioInputStream(new File("sounds/bgm.wav"));
                backgroundMusic = AudioSystem.getClip();
                backgroundMusic.open(musicStream);
            } catch (Exception e) {
                // Create fallback background music
                backgroundMusic = createFallbackMusic();
            }

        } catch (Exception e) {
            System.err.println("Error loading sounds: " + e.getMessage());
            e.printStackTrace();
            soundEnabled = false;
        }
    }

    /**
     * Create a fallback sound if sound file is missing
     */
    private Clip createFallbackSound(int soundType) {
        try {
            // Create synthesized sound based on type
            byte[] buffer = new byte[8000];
            AudioFormat format = new AudioFormat(8000f, 8, 1, true, false);

            // Different frequencies for different sound types
            int baseFreq = 220 + (soundType * 110);

            // Generate a simple tone
            for (int i = 0; i < buffer.length; i++) {
                double angle = i / (8000.0 / baseFreq) * 2.0 * Math.PI;
                buffer[i] = (byte)(Math.sin(angle) * 100);
            }

            // Create clip from buffer
            AudioInputStream ais = new AudioInputStream(
                    new ByteArrayInputStream(buffer),
                    format,
                    buffer.length
            );

            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            return clip;

        } catch (Exception e) {
            System.err.println("Failed to create fallback sound: " + e.getMessage());
            return null;
        }
    }

    /**
     * Create fallback background music
     */
    private Clip createFallbackMusic() {
        try {
            // Create a simple looping tone sequence
            int sampleRate = 44100;
            byte[] buffer = new byte[sampleRate * 4]; // 4 seconds of audio
            AudioFormat format = new AudioFormat(sampleRate, 8, 1, true, false);

            // Generate a simple melody
            int[] notes = {262, 330, 392, 523, 392, 330, 262, 196};
            int noteDuration = sampleRate / 2; // Half second per note

            for (int note = 0; note < notes.length; note++) {
                int noteFreq = notes[note];
                for (int i = 0; i < noteDuration; i++) {
                    int bufferPos = note * noteDuration + i;
                    if (bufferPos < buffer.length) {
                        double angle = i / (sampleRate / (double)noteFreq) * 2.0 * Math.PI;
                        buffer[bufferPos] = (byte)(Math.sin(angle) * 50); // Lower volume
                    }
                }
            }

            // Create clip from buffer
            AudioInputStream ais = new AudioInputStream(
                    new ByteArrayInputStream(buffer),
                    format,
                    buffer.length
            );

            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            return clip;

        } catch (Exception e) {
            System.err.println("Failed to create fallback music: " + e.getMessage());
            return null;
        }
    }

    /**
     * Play a specific sound effect
     */
    public void playSound(int soundIndex) {
        if (!soundEnabled || soundIndex < 0 || soundIndex >= soundClips.length) {
            return;
        }

        Clip clip = soundClips[soundIndex];
        if (clip != null) {
            clip.setFramePosition(0);
            clip.start();
        }
    }

    /**
     * Play background music in loop
     */
    public void playBackgroundMusic() {
        if (!soundEnabled || backgroundMusic == null) {
            return;
        }

        backgroundMusic.setFramePosition(0);
        backgroundMusic.loop(Clip.LOOP_CONTINUOUSLY);
    }

    /**
     * Stop background music
     */
    public void stopBackgroundMusic() {
        if (backgroundMusic != null && backgroundMusic.isRunning()) {
            backgroundMusic.stop();
        }
    }

    /**
     * Stop all sounds
     */
    public void stopAll() {
        stopBackgroundMusic();

        for (Clip clip : soundClips) {
            if (clip != null && clip.isRunning()) {
                clip.stop();
            }
        }
    }
}