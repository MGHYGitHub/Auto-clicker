package com.example.autoclicker;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseListener;
import com.github.kwhat.jnativehook.mouse.NativeMouseMotionListener;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JComboBox;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicComboBoxUI;
import java.awt.AWTException;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.Image;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/** ClickFlow：支持独立点位参数、配置持久化与全局快捷键的桌面连点器。 */
public class AutoClickerApp extends JFrame implements NativeKeyListener, NativeMouseListener, NativeMouseMotionListener {
    private static final Color BACKGROUND = Color.WHITE;
    private static final Color CARD = new Color(255, 255, 255, 220);
    private static final Color TEXT = new Color(28, 34, 45);
    private static final Color SUBTLE_TEXT = new Color(107, 114, 128);
    private static final Color BLUE = new Color(0, 122, 255);
    private static final Color ORANGE = new Color(255, 149, 0);
    private static final Color RED = new Color(255, 69, 58);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path appDirectory = Path.of(System.getProperty("user.home"), ".clickflow");
    private final Path defaultConfigFile = appDirectory.resolve("config.properties");
    private final Path logFile = appDirectory.resolve("clickflow.log");
    private Path currentConfigFile = defaultConfigFile;

    private final DefaultListModel<String> pointListModel = new DefaultListModel<>();
    private final JList<String> pointList = new JList<>(pointListModel);
    private final List<ClickPoint> savedPoints = new ArrayList<>();
    private final JLabel statusLabel = new JLabel("准备就绪", SwingConstants.CENTER);
    private final JLabel taskSummaryLabel = new JLabel("尚未添加任务点位", SwingConstants.CENTER);
    private final JLabel countLabel = new JLabel("0 次点击", SwingConstants.CENTER);
    private final RoundButton startButton = new RoundButton("启动", BLUE);
    private final RoundButton pauseButton = new RoundButton("暂停", ORANGE);
    private final RoundButton stopButton = new RoundButton("停止", RED);
    private final RoundButton deleteButton = new RoundButton("删除选中", RED);
    private final RoundButton moveUpButton = new RoundButton("上移", new Color(94, 92, 230));
    private final RoundButton moveDownButton = new RoundButton("下移", new Color(94, 92, 230));
    private final RoundButton clearButton = new RoundButton("清空", new Color(235, 237, 242));
    private final RoundButton recordButton = new RoundButton("开始录制", new Color(175, 82, 222));
    private final RoundButton clearRecordingButton = new RoundButton("清除录制", new Color(235, 237, 242));
    private final JComboBox<String> taskModeBox = new JComboBox<>(new String[]{"点位任务", "录制回放"});
    private final JLabel recordingSummaryLabel = new JLabel("未录制鼠标操作", SwingConstants.CENTER);

    private int defaultStartDelayMs = 0;
    private int defaultIntervalMs = 100;
    private int defaultClicks = 1;
    /** 0 表示无限循环。 */
    private int taskCycleLimit = 0;

    private final Robot robot;
    private Timer clickTimer;
    private Runnable scheduledAction;
    private long clickCount;
    private int currentPointIndex;
    private int remainingClicksAtPoint;
    private int completedTaskCycles;
    private LocalDateTime taskStartedAt;
    private final List<RecordedMouseAction> recordedActions = new ArrayList<>();
    private boolean recording;
    private long lastRecordedAt;
    private long lastMoveRecordedAt;
    private int replayIndex;
    private int resizeCursorType = Cursor.DEFAULT_CURSOR;
    private Point resizeScreenAnchor;
    private Rectangle resizeBoundsAnchor;

    public AutoClickerApp() throws AWTException {
        super("ClickFlow · 自动化连点器");
        robot = new Robot();
        robot.setAutoDelay(0);
        setUndecorated(true);
        loadConfiguration(defaultConfigFile, false);
        createInterface();
        setIconImage(loadAppIcon());
        bindActions();
        installResizeSupport();
        registerGlobalHotkeys();
        refreshInterface();
        appendLog("程序已启动");
    }

    private void createInterface() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setJMenuBar(createMenuBar());

        JPanel content = new GlassBackgroundPanel();
        content.setLayout(new BorderLayout(0, 18));
        content.setBorder(BorderFactory.createEmptyBorder(26, 30, 26, 30));

        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        JLabel title = new JLabel("ClickFlow");
        title.setForeground(TEXT);
        title.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 30));
        JLabel subtitle = new JLabel("按 F3 记录坐标 · 双击点位修改独立参数");
        subtitle.setForeground(SUBTLE_TEXT);
        subtitle.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(new MacWindowControls(), BorderLayout.EAST);
        DragWindowAdapter dragAdapter = new DragWindowAdapter();
        header.addMouseListener(dragAdapter);
        header.addMouseMotionListener(dragAdapter);
        titleRow.addMouseListener(dragAdapter);
        titleRow.addMouseMotionListener(dragAdapter);
        title.addMouseListener(dragAdapter);
        title.addMouseMotionListener(dragAdapter);
        subtitle.addMouseListener(dragAdapter);
        subtitle.addMouseMotionListener(dragAdapter);
        header.add(titleRow, BorderLayout.NORTH);
        header.add(subtitle, BorderLayout.SOUTH);
        content.add(header, BorderLayout.NORTH);

        JPanel mainCard = new RoundPanel(CARD, 22);
        mainCard.setLayout(new BorderLayout(0, 12));
        mainCard.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        JPanel listHeader = new JPanel(new BorderLayout());
        listHeader.setOpaque(false);
        JLabel listTitle = new JLabel("任务点位");
        listTitle.setForeground(TEXT);
        listTitle.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 16));
        listHeader.add(listTitle, BorderLayout.WEST);
        JPanel listActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        listActions.setOpaque(false);
        listActions.add(moveUpButton);
        listActions.add(moveDownButton);
        listActions.add(deleteButton);
        listActions.add(clearButton);
        listHeader.add(listActions, BorderLayout.EAST);
        mainCard.add(listHeader, BorderLayout.NORTH);

        pointList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        pointList.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
        pointList.setForeground(TEXT);
        pointList.setBackground(CARD);
        pointList.setFixedCellHeight(36);
        pointList.setCellRenderer(new MacPointRenderer());
        JScrollPane scrollPane = new JScrollPane(pointList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(CARD);
        mainCard.add(scrollPane, BorderLayout.CENTER);

        taskSummaryLabel.setForeground(SUBTLE_TEXT);
        taskSummaryLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        JPanel taskPill = new RoundPanel(new Color(230, 242, 255, 210), 14);
        taskPill.setLayout(new BorderLayout());
        taskPill.setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 12));
        taskPill.add(taskSummaryLabel, BorderLayout.CENTER);
        mainCard.add(taskPill, BorderLayout.SOUTH);

        JPanel workspace = new JPanel(new BorderLayout(0, 12));
        workspace.setOpaque(false);
        workspace.add(mainCard, BorderLayout.CENTER);
        JPanel recordingCard = new RoundPanel(new Color(255, 255, 255, 180), 18);
        recordingCard.setLayout(new BorderLayout(10, 0));
        recordingCard.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JLabel recordingTitle = new JLabel("鼠标录制 · F7");
        recordingTitle.setForeground(TEXT);
        recordingTitle.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 14));
        recordingCard.add(recordingTitle, BorderLayout.WEST);
        recordingSummaryLabel.setForeground(SUBTLE_TEXT);
        recordingCard.add(recordingSummaryLabel, BorderLayout.CENTER);
        JPanel recordingActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        recordingActions.setOpaque(false);
        recordingActions.add(recordButton);
        recordingActions.add(clearRecordingButton);
        recordingCard.add(recordingActions, BorderLayout.EAST);
        workspace.add(recordingCard, BorderLayout.SOUTH);
        content.add(workspace, BorderLayout.CENTER);

        JPanel footer = new JPanel(new GridBagLayout());
        footer.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        statusLabel.setForeground(SUBTLE_TEXT);
        statusLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        footer.add(statusLabel, c);
        c.gridy = 1; c.insets = new Insets(6, 0, 12, 0);
        countLabel.setForeground(TEXT);
        countLabel.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 17));
        footer.add(countLabel, c);
        c.gridy = 2; c.insets = new Insets(0, 0, 10, 0);
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        modePanel.setOpaque(false);
        JLabel modeLabel = new JLabel("执行模式");
        modeLabel.setForeground(SUBTLE_TEXT);
        modePanel.add(modeLabel);
        taskModeBox.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        taskModeBox.setUI(new MacComboBoxUI());
        taskModeBox.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 6));
        taskModeBox.setBackground(new Color(255, 255, 255, 215));
        modePanel.add(taskModeBox);
        footer.add(modePanel, c);
        c.gridy = 3; c.insets = new Insets(0, 0, 0, 0);
        JPanel buttons = new JPanel(new GridBagLayout());
        buttons.setOpaque(false);
        GridBagConstraints bc = new GridBagConstraints();
        bc.weightx = 1; bc.fill = GridBagConstraints.HORIZONTAL; bc.insets = new Insets(0, 0, 0, 8);
        buttons.add(startButton, bc); bc.gridx = 1;
        buttons.add(pauseButton, bc); bc.gridx = 2; bc.insets = new Insets(0, 0, 0, 0);
        buttons.add(stopButton, bc);
        footer.add(buttons, c);
        content.add(footer, BorderLayout.SOUTH);

        setContentPane(content);
        pack();
        setMinimumSize(new Dimension(560, 530));
        setLocationRelativeTo(null);
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("文件");
        fileMenu.add(menuItem("导入点位配置", this::importConfiguration));
        fileMenu.add(menuItem("导出点位配置", this::exportConfiguration));
        fileMenu.addSeparator();
        fileMenu.add(menuItem("保存当前配置", this::saveCurrentConfiguration));
        fileMenu.add(menuItem("另存为...", this::saveConfigurationAs));
        fileMenu.addSeparator();
        fileMenu.add(menuItem("导出任务执行报告", this::exportExecutionReport));

        JMenu settingsMenu = new JMenu("设置");
        settingsMenu.add(menuItem("设置任务界面", this::showGlobalSettingsDialog));

        JMenu helpMenu = new JMenu("帮助");
        helpMenu.add(menuItem("使用说明", this::showUsage));
        helpMenu.add(menuItem("打开日志文件", this::openLogFile));
        helpMenu.add(menuItem("检查更新", this::checkForUpdates));
        menuBar.add(fileMenu);
        menuBar.add(settingsMenu);
        menuBar.add(helpMenu);
        return menuBar;
    }

    private JMenuItem menuItem(String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(event -> action.run());
        return item;
    }

    private void bindActions() {
        startButton.addActionListener(event -> startClicking());
        pauseButton.addActionListener(event -> pauseOrResume());
        stopButton.addActionListener(event -> stopClicking("用户停止"));
        deleteButton.addActionListener(event -> deleteSelectedPoint());
        moveUpButton.addActionListener(event -> moveSelectedPoint(-1));
        moveDownButton.addActionListener(event -> moveSelectedPoint(1));
        clearButton.addActionListener(event -> clearPoints());
        recordButton.addActionListener(event -> toggleRecording());
        clearRecordingButton.addActionListener(event -> clearRecording());
        taskModeBox.addActionListener(event -> refreshControls());
        recordingSummaryLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        recordingSummaryLabel.setToolTipText("双击编辑录制动作");
        recordingSummaryLabel.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) showRecordingEditor();
            }
        });
        pointList.addListSelectionListener(event -> { if (!event.getValueIsAdjusting()) refreshControls(); });
        pointList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) showPointEditor();
            }
        });
    }

    private void registerGlobalHotkeys() {
        try {
            Logger.getLogger(GlobalScreen.class.getPackageName()).setLevel(Level.OFF);
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(this);
            GlobalScreen.addNativeMouseListener(this);
            GlobalScreen.addNativeMouseMotionListener(this);
            addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent event) {
                    saveQuietly();
                    unregisterGlobalHotkeys();
                }
            });
        } catch (Exception exception) {
            appendLog("全局快捷键注册失败：" + exception.getMessage());
        }
    }

    private void unregisterGlobalHotkeys() {
        if (GlobalScreen.isNativeHookRegistered()) {
            GlobalScreen.removeNativeKeyListener(this);
            GlobalScreen.removeNativeMouseListener(this);
            GlobalScreen.removeNativeMouseMotionListener(this);
            try { GlobalScreen.unregisterNativeHook(); } catch (Exception ignored) { }
        }
    }

    @Override public void nativeKeyPressed(NativeKeyEvent event) {
        if (recording && !isApplicationHotkey(event.getKeyCode())) recordKeyboardAction(RecordedActionType.KEY_PRESS, event);
        switch (event.getKeyCode()) {
            case NativeKeyEvent.VC_F3 -> SwingUtilities.invokeLater(() -> { if (!recording) saveMousePosition(); });
            case NativeKeyEvent.VC_F4 -> SwingUtilities.invokeLater(this::startClicking);
            case NativeKeyEvent.VC_F5 -> SwingUtilities.invokeLater(this::pauseOrResume);
            case NativeKeyEvent.VC_F6 -> SwingUtilities.invokeLater(() -> stopClicking("快捷键停止"));
            case NativeKeyEvent.VC_F7 -> SwingUtilities.invokeLater(this::toggleRecording);
            default -> { }
        }
    }
    @Override public void nativeKeyReleased(NativeKeyEvent event) {
        if (recording && !isApplicationHotkey(event.getKeyCode())) recordKeyboardAction(RecordedActionType.KEY_RELEASE, event);
    }

    private boolean isApplicationHotkey(int code) {
        return code == NativeKeyEvent.VC_F3 || code == NativeKeyEvent.VC_F4 || code == NativeKeyEvent.VC_F5 || code == NativeKeyEvent.VC_F6 || code == NativeKeyEvent.VC_F7;
    }

    private void recordKeyboardAction(RecordedActionType type, NativeKeyEvent event) {
        long now = System.currentTimeMillis();
        int delay = (int) Math.min(60_000, Math.max(0, now - lastRecordedAt));
        lastRecordedAt = now;
        recordedActions.add(new RecordedMouseAction(type, 0, 0, 0, delay, event.getKeyCode()));
        SwingUtilities.invokeLater(this::refreshRecordingSummary);
    }

    @Override public void nativeMousePressed(NativeMouseEvent event) { recordMouseAction(RecordedActionType.PRESS, event); }
    @Override public void nativeMouseReleased(NativeMouseEvent event) { recordMouseAction(RecordedActionType.RELEASE, event); }
    @Override public void nativeMouseMoved(NativeMouseEvent event) { recordMouseMove(event); }
    @Override public void nativeMouseDragged(NativeMouseEvent event) { recordMouseMove(event); }

    private void recordMouseMove(NativeMouseEvent event) {
        if (!recording) return;
        long now = System.currentTimeMillis();
        if (now - lastMoveRecordedAt < 40) return;
        lastMoveRecordedAt = now;
        recordMouseAction(RecordedActionType.MOVE, event);
    }

    private void recordMouseAction(RecordedActionType type, NativeMouseEvent event) {
        if (!recording) return;
        long now = System.currentTimeMillis();
        int delay = (int) Math.min(60_000, Math.max(0, now - lastRecordedAt));
        lastRecordedAt = now;
        // 使用 AWT 当前指针坐标而非原生钩子坐标，避免 Windows 高 DPI 缩放导致回放偏移。
        Point position = MouseInfo.getPointerInfo() == null ? new Point(event.getX(), event.getY()) : MouseInfo.getPointerInfo().getLocation();
        recordedActions.add(new RecordedMouseAction(type, position.x, position.y, event.getButton(), delay));
        SwingUtilities.invokeLater(this::refreshRecordingSummary);
    }

    private void toggleRecording() { if (recording) stopRecording(); else startRecording(); }
    private void startRecording() {
        if (clickTimer != null) { statusLabel.setText("任务运行中，不能开始录制"); return; }
        recordedActions.clear();
        recording = true;
        lastRecordedAt = System.currentTimeMillis();
        lastMoveRecordedAt = 0;
        recordButton.setText("停止录制 (F7)");
        statusLabel.setForeground(RED);
        statusLabel.setText("录制中 · 执行鼠标操作，按 F7 停止录制");
        refreshControls();
        appendLog("开始录制鼠标操作");
    }

    private void stopRecording() {
        if (!recording) return;
        recording = false;
        recordButton.setText("开始录制");
        statusLabel.setForeground(SUBTLE_TEXT);
        statusLabel.setText("录制完成，共 " + recordedActions.size() + " 个鼠标动作");
        saveQuietly();
        refreshRecordingSummary();
        refreshControls();
        appendLog("结束录制鼠标操作，共 " + recordedActions.size() + " 个动作");
    }

    private void clearRecording() {
        if (recording || clickTimer != null) return;
        recordedActions.clear();
        saveQuietly();
        refreshRecordingSummary();
        statusLabel.setText("已清除录制内容");
    }

    private void showRecordingEditor() {
        if (recording || clickTimer != null || recordedActions.isEmpty()) return;
        DefaultListModel<String> model = new DefaultListModel<>();
        JList<String> actionList = new JList<>(model);
        actionList.setCellRenderer(new MacPointRenderer());
        actionList.setFixedCellHeight(32);
        refreshRecordingActionList(model);
        JPanel root = new RoundPanel(new Color(255, 255, 255, 245), 20);
        root.setLayout(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        JLabel title = new JLabel("录制动作 · 双击动作编辑参数");
        title.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 16));
        title.setForeground(TEXT);
        root.add(title, BorderLayout.NORTH);
        root.add(new JScrollPane(actionList), BorderLayout.CENTER);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 7, 0));
        controls.setOpaque(false);
        RoundButton up = new RoundButton("上移", new Color(94, 92, 230));
        RoundButton down = new RoundButton("下移", new Color(94, 92, 230));
        RoundButton remove = new RoundButton("删除", RED);
        RoundButton done = new RoundButton("完成", BLUE);
        controls.add(up); controls.add(down); controls.add(remove); controls.add(done);
        root.add(controls, BorderLayout.SOUTH);
        JDialog dialog = createMacDialog("编辑录制", root);
        actionList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) { if (event.getClickCount() == 2) editRecordedAction(actionList.getSelectedIndex(), model); }
        });
        up.addActionListener(event -> moveRecordedAction(actionList, model, -1));
        down.addActionListener(event -> moveRecordedAction(actionList, model, 1));
        remove.addActionListener(event -> {
            int index = actionList.getSelectedIndex();
            if (index >= 0) { recordedActions.remove(index); refreshRecordingActionList(model); if (!recordedActions.isEmpty()) actionList.setSelectedIndex(Math.min(index, recordedActions.size() - 1)); }
        });
        done.addActionListener(event -> { saveQuietly(); refreshRecordingSummary(); dialog.dispose(); });
        dialog.setVisible(true);
    }

    private void editRecordedAction(int index, DefaultListModel<String> model) {
        if (index < 0 || index >= recordedActions.size()) return;
        RecordedMouseAction action = recordedActions.get(index);
        JComboBox<RecordedActionType> typeBox = new JComboBox<>(RecordedActionType.values());
        typeBox.setSelectedItem(action.type);
        JSpinner x = spinner(action.x, -20_000, 20_000, 1);
        JSpinner y = spinner(action.y, -20_000, 20_000, 1);
        JSpinner delay = spinner(action.delayMs, 0, 60_000, 10);
        JPanel fields = dialogFields();
        addDialogRow(fields, 0, "动作类型", typeBox, "");
        addDialogRow(fields, 1, "X 坐标", x, "像素");
        addDialogRow(fields, 2, "Y 坐标", y, "像素");
        addDialogRow(fields, 3, "执行前延迟", delay, "毫秒");
        if (showMacConfirmDialog("编辑录制动作 " + (index + 1), fields) == JOptionPane.OK_OPTION) {
            recordedActions.set(index, new RecordedMouseAction((RecordedActionType) typeBox.getSelectedItem(), intValue(x), intValue(y), action.button, intValue(delay), action.keyCode));
            refreshRecordingActionList(model);
            saveQuietly();
        }
    }

    private void moveRecordedAction(JList<String> list, DefaultListModel<String> model, int direction) {
        int index = list.getSelectedIndex();
        int target = index + direction;
        if (index < 0 || target < 0 || target >= recordedActions.size()) return;
        Collections.swap(recordedActions, index, target);
        refreshRecordingActionList(model);
        list.setSelectedIndex(target);
    }

    private void refreshRecordingActionList(DefaultListModel<String> model) {
        model.clear();
        for (int i = 0; i < recordedActions.size(); i++) {
            RecordedMouseAction action = recordedActions.get(i);
            String detail = (action.type == RecordedActionType.KEY_PRESS || action.type == RecordedActionType.KEY_RELEASE) ? NativeKeyEvent.getKeyText(action.keyCode) : "(" + action.x + ", " + action.y + ")";
            model.addElement(String.format("%02d  ·  %s   %s   延迟 %dms", i + 1, action.type, detail, action.delayMs));
        }
    }

    private void showGlobalSettingsDialog() {
        if (clickTimer != null) return;
        JSpinner startDelay = spinner(defaultStartDelayMs, 0, 60_000, 50);
        JSpinner interval = spinner(defaultIntervalMs, 10, 60_000, 10);
        JSpinner clicks = spinner(defaultClicks, 1, 100_000, 1);
        JSpinner cycles = spinner(taskCycleLimit, 0, 100_000, 1);
        JCheckBox applyToAll = new JCheckBox("同时应用前三项参数到全部已有点位");
        applyToAll.setOpaque(false);
        JPanel fields = dialogFields();
        addDialogRow(fields, 0, "新点位开始前延迟", startDelay, "毫秒");
        addDialogRow(fields, 1, "新点位连续点击间隔", interval, "毫秒");
        addDialogRow(fields, 2, "新点位点击次数", clicks, "次");
        addDialogRow(fields, 3, "任务点位循环次数", cycles, "0 = 无限循环");
        JLabel tip = new JLabel("这些默认值只影响之后按 F3 新建的点位。", SwingConstants.CENTER);
        tip.setForeground(SUBTLE_TEXT);
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(14, 16, 8, 16));
        panel.add(fields, BorderLayout.CENTER);
        JPanel dialogFooter = new JPanel(new BorderLayout(0, 6));
        dialogFooter.setOpaque(false);
        dialogFooter.add(applyToAll, BorderLayout.NORTH);
        dialogFooter.add(tip, BorderLayout.SOUTH);
        panel.add(dialogFooter, BorderLayout.SOUTH);
        int choice = showMacConfirmDialog("设置任务", panel);
        if (choice == JOptionPane.OK_OPTION) {
            defaultStartDelayMs = intValue(startDelay);
            defaultIntervalMs = intValue(interval);
            defaultClicks = intValue(clicks);
            taskCycleLimit = intValue(cycles);
            if (applyToAll.isSelected()) {
                for (ClickPoint point : savedPoints) {
                    point.startDelayMs = defaultStartDelayMs;
                    point.intervalMs = defaultIntervalMs;
                    point.clicks = defaultClicks;
                }
            }
            saveQuietly();
            refreshInterface();
            statusLabel.setText("全局默认参数已保存");
            appendLog("更新全局任务设置");
        }
    }

    private void showPointEditor() {
        if (clickTimer != null) return;
        int index = pointList.getSelectedIndex();
        if (index < 0) return;
        ClickPoint point = savedPoints.get(index);
        JTextField nameField = new JTextField(point.name, 18);
        JComboBox<PointActionType> actionType = new JComboBox<>(PointActionType.values());
        actionType.setSelectedItem(point.actionType);
        JSpinner wheelSteps = spinner(point.wheelSteps, -100, 100, 1);
        HotkeyCaptureField hotkey = new HotkeyCaptureField(point.hotkey);
        JSpinner startDelay = spinner(point.startDelayMs, 0, 60_000, 50);
        JSpinner interval = spinner(point.intervalMs, 10, 60_000, 10);
        JSpinner clicks = spinner(point.clicks, 1, 100_000, 1);
        JPanel fields = dialogFields();
        addDialogRow(fields, 0, "点位名称", nameField, "");
        addDialogRow(fields, 1, "动作", actionType, "滚轮正数向上");
        addDialogRow(fields, 2, "滚轮步数", wheelSteps, "负数向下");
        addDialogRow(fields, 3, "快捷键", hotkey, "点击后直接按组合键");
        addDialogRow(fields, 4, "开始前延迟", startDelay, "毫秒");
        addDialogRow(fields, 5, "连续点击间隔", interval, "毫秒");
        addDialogRow(fields, 6, "此点位执行次数", clicks, "次");
        int choice = showMacConfirmDialog("编辑点位 " + (index + 1) + "  ·  (" + point.x + ", " + point.y + ")", fields);
        if (choice == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            point.name = name.isEmpty() ? "点位 " + (index + 1) : name;
            point.actionType = (PointActionType) actionType.getSelectedItem();
            point.wheelSteps = intValue(wheelSteps);
            point.hotkey = hotkey.getHotkey();
            point.startDelayMs = intValue(startDelay);
            point.intervalMs = intValue(interval);
            point.clicks = intValue(clicks);
            saveQuietly();
            refreshInterface();
            pointList.setSelectedIndex(index);
            statusLabel.setText("点位 " + (index + 1) + " 已更新");
            appendLog("更新点位 " + (index + 1));
        }
    }

    private JPanel dialogFields() {
        JPanel fields = new JPanel(new GridBagLayout());
        fields.setBorder(BorderFactory.createEmptyBorder(8, 14, 4, 14));
        return fields;
    }

    private int showMacConfirmDialog(String title, JPanel content) {
        RoundButton save = new RoundButton("保存", BLUE);
        RoundButton cancel = new RoundButton("取消", new Color(142, 142, 147));
        JDialog dialog = new JDialog(this, title, true);
        dialog.setUndecorated(true);
        dialog.setResizable(false);
        final boolean[] saved = {false};
        JPanel root = new RoundPanel(new Color(255, 255, 255, 248), 20);
        root.setLayout(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setOpaque(false);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 14));
        titleLabel.setForeground(TEXT);
        JPanel closeControl = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        closeControl.setOpaque(false);
        closeControl.add(new MacCircleButton(new Color(255, 95, 86), "×", dialog::dispose));
        titleBar.add(titleLabel, BorderLayout.WEST);
        titleBar.add(closeControl, BorderLayout.EAST);
        root.add(titleBar, BorderLayout.NORTH);
        root.add(content, BorderLayout.CENTER);
        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonBar.setOpaque(false);
        save.addActionListener(event -> { saved[0] = true; dialog.dispose(); });
        cancel.addActionListener(event -> dialog.dispose());
        buttonBar.add(cancel);
        buttonBar.add(save);
        root.add(buttonBar, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(420, dialog.getHeight()));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        repaint();
        return saved[0] ? JOptionPane.OK_OPTION : JOptionPane.CANCEL_OPTION;
    }

    private void addDialogRow(JPanel panel, int row, String text, JSpinner spinner, String unit) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row; c.insets = new Insets(5, 4, 5, 4); c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0; c.weightx = 1;
        panel.add(new JLabel(text), c);
        spinner.setPreferredSize(new Dimension(125, 30));
        c.gridx = 1; c.weightx = 0;
        panel.add(spinner, c);
        JLabel unitLabel = new JLabel(unit);
        unitLabel.setForeground(SUBTLE_TEXT);
        c.gridx = 2;
        panel.add(unitLabel, c);
    }

    private void addDialogRow(JPanel panel, int row, String text, JTextField field, String unit) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row; c.insets = new Insets(5, 4, 5, 4); c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0; c.weightx = 1;
        panel.add(new JLabel(text), c);
        c.gridx = 1; c.weightx = 0;
        panel.add(field, c);
        c.gridx = 2;
        panel.add(new JLabel(unit), c);
    }

    private void addDialogRow(JPanel panel, int row, String text, JComboBox<?> comboBox, String unit) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row; c.insets = new Insets(5, 4, 5, 4); c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0; c.weightx = 1;
        panel.add(new JLabel(text), c);
        c.gridx = 1; c.weightx = 0;
        comboBox.setUI(new MacComboBoxUI());
        panel.add(comboBox, c);
        c.gridx = 2;
        panel.add(new JLabel(unit), c);
    }

    private JDialog createMacDialog(String title, JPanel content) {
        JDialog dialog = new JDialog(this, title, true);
        dialog.setUndecorated(true);
        JPanel shell = new GlassBackgroundPanel();
        shell.setLayout(new BorderLayout(0, 8));
        shell.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setOpaque(false);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(TEXT);
        titleLabel.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 14));
        JPanel dialogControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        dialogControls.setOpaque(false);
        dialogControls.add(new MacCircleButton(new Color(255, 95, 86), "×", dialog::dispose));
        dialogControls.add(new MacCircleButton(new Color(255, 189, 46), "−", () -> { }));
        titleBar.add(dialogControls, BorderLayout.WEST);
        titleBar.add(titleLabel, BorderLayout.CENTER);
        shell.add(titleBar, BorderLayout.NORTH);
        shell.add(content, BorderLayout.CENTER);
        dialog.setContentPane(shell);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(420, 260));
        dialog.setLocationRelativeTo(this);
        return dialog;
    }

    private JSpinner spinner(int value, int min, int max, int step) {
        return new JSpinner(new SpinnerNumberModel(value, min, max, step));
    }

    private void saveMousePosition() {
        if (clickTimer != null) {
            statusLabel.setText("任务运行中，请先按 F6 停止");
            return;
        }
        Point position = MouseInfo.getPointerInfo().getLocation();
        savedPoints.add(new ClickPoint("点位 " + (savedPoints.size() + 1), position.x, position.y, defaultStartDelayMs, defaultIntervalMs, defaultClicks));
        saveQuietly();
        refreshInterface();
        pointList.setSelectedIndex(savedPoints.size() - 1);
        statusLabel.setForeground(new Color(0, 135, 75));
        statusLabel.setText("已添加坐标 (" + position.x + ", " + position.y + ")");
        appendLog("添加点位 (" + position.x + ", " + position.y + ")");
    }

    private void startClicking() {
        if (isClicking()) return;
        if (taskModeBox.getSelectedIndex() == 1) {
            startRecordingPlayback();
            return;
        }
        if (savedPoints.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先移动鼠标到目标位置，再按 F3 添加任务点位。", "尚未添加点位", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        clickCount = 0;
        completedTaskCycles = 0;
        currentPointIndex = 0;
        taskStartedAt = LocalDateTime.now();
        countLabel.setText("0 次点击");
        clickTimer = new Timer(1, event -> runScheduledAction());
        statusLabel.setForeground(new Color(0, 135, 75));
        statusLabel.setText("运行中 · F5 暂停 · F6 立即停止");
        scheduleNextPoint();
        refreshControls();
        appendLog("任务启动");
    }

    private void startRecordingPlayback() {
        if (recordedActions.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先录制鼠标操作，再选择录制回放模式启动。", "没有录制内容", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        clickCount = 0;
        completedTaskCycles = 0;
        replayIndex = 0;
        taskStartedAt = LocalDateTime.now();
        countLabel.setText("0 次点击");
        clickTimer = new Timer(1, event -> runScheduledAction());
        statusLabel.setForeground(new Color(0, 135, 75));
        statusLabel.setText("录制回放中 · F5 暂停 · F6 立即停止");
        scheduleNextRecordedAction();
        refreshControls();
        appendLog("开始录制回放");
    }

    private void scheduleNextRecordedAction() {
        if (clickTimer == null) return;
        RecordedMouseAction action = recordedActions.get(replayIndex);
        schedule(action.delayMs, this::runRecordedAction);
    }

    private void runRecordedAction() {
        if (clickTimer == null) return;
        RecordedMouseAction action = recordedActions.get(replayIndex);
        if (action.type == RecordedActionType.MOVE || action.type == RecordedActionType.PRESS || action.type == RecordedActionType.RELEASE) robot.mouseMove(action.x, action.y);
        if (action.type == RecordedActionType.PRESS) robot.mousePress(toRobotButtonMask(action.button));
        if (action.type == RecordedActionType.RELEASE) {
            robot.mouseRelease(toRobotButtonMask(action.button));
            clickCount++;
            countLabel.setText(clickCount + " 次点击 · 已完成 " + completedTaskCycles + " 轮");
        }
        int replayKeyCode = toRobotKeyCode(action.keyCode);
        if (action.type == RecordedActionType.KEY_PRESS && replayKeyCode != KeyEvent.VK_UNDEFINED) robot.keyPress(replayKeyCode);
        if (action.type == RecordedActionType.KEY_RELEASE && replayKeyCode != KeyEvent.VK_UNDEFINED) robot.keyRelease(replayKeyCode);
        replayIndex++;
        if (replayIndex >= recordedActions.size()) {
            completedTaskCycles++;
            if (taskCycleLimit > 0 && completedTaskCycles >= taskCycleLimit) {
                stopClicking("录制回放已完成");
                return;
            }
            replayIndex = 0;
        }
        scheduleNextRecordedAction();
    }

    private int toRobotButtonMask(int nativeButton) {
        return switch (nativeButton) {
            case NativeMouseEvent.BUTTON2 -> InputEvent.BUTTON2_DOWN_MASK;
            case NativeMouseEvent.BUTTON3 -> InputEvent.BUTTON3_DOWN_MASK;
            default -> InputEvent.BUTTON1_DOWN_MASK;
        };
    }

    private int toRobotKeyCode(int nativeKeyCode) {
        String keyText = NativeKeyEvent.getKeyText(nativeKeyCode);
        return toRobotKeyCode(keyText == null ? "" : keyText.toUpperCase());
    }

    private void executeHotkey(String expression) {
        if (expression == null || expression.isBlank()) return;
        List<Integer> keys = new ArrayList<>();
        for (String part : expression.toUpperCase().split("\\+")) {
            int key = toRobotKeyCode(part.trim());
            if (key != KeyEvent.VK_UNDEFINED) keys.add(key);
        }
        for (int key : keys) robot.keyPress(key);
        for (int i = keys.size() - 1; i >= 0; i--) robot.keyRelease(keys.get(i));
    }

    private int toRobotKeyCode(String key) {
        return switch (key) {
            case "CTRL", "CONTROL" -> KeyEvent.VK_CONTROL;
            case "ALT" -> KeyEvent.VK_ALT;
            case "SHIFT" -> KeyEvent.VK_SHIFT;
            case "WIN", "WINDOWS", "META" -> KeyEvent.VK_WINDOWS;
            case "ENTER" -> KeyEvent.VK_ENTER;
            case "ESC", "ESCAPE" -> KeyEvent.VK_ESCAPE;
            case "SPACE" -> KeyEvent.VK_SPACE;
            case "TAB" -> KeyEvent.VK_TAB;
            default -> {
                if (key.matches("F([1-9]|1[0-2])")) yield KeyEvent.VK_F1 + Integer.parseInt(key.substring(1)) - 1;
                if (key.length() == 1) yield KeyEvent.getExtendedKeyCodeForChar(key.charAt(0));
                yield KeyEvent.VK_UNDEFINED;
            }
        };
    }

    private void scheduleNextPoint() {
        if (clickTimer == null) return;
        ClickPoint point = savedPoints.get(currentPointIndex);
        remainingClicksAtPoint = point.clicks;
        schedule(point.startDelayMs, this::clickCurrentPoint);
    }

    private void clickCurrentPoint() {
        if (clickTimer == null) return;
        ClickPoint point = savedPoints.get(currentPointIndex);
        robot.mouseMove(point.x, point.y);
        switch (point.actionType) {
            case LEFT_CLICK -> { robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); }
            case RIGHT_CLICK -> { robot.mousePress(InputEvent.BUTTON3_DOWN_MASK); robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK); }
            case WHEEL -> robot.mouseWheel(point.wheelSteps);
            case HOTKEY -> executeHotkey(point.hotkey);
        }
        clickCount++;
        countLabel.setText(clickCount + " 次点击 · 已完成 " + completedTaskCycles + " 轮");
        remainingClicksAtPoint--;
        if (remainingClicksAtPoint > 0) {
            schedule(point.intervalMs, this::clickCurrentPoint);
            return;
        }
        currentPointIndex++;
        if (currentPointIndex >= savedPoints.size()) {
            completedTaskCycles++;
            if (taskCycleLimit > 0 && completedTaskCycles >= taskCycleLimit) {
                stopClicking("已完成设定循环次数");
                return;
            }
            currentPointIndex = 0;
        }
        scheduleNextPoint();
    }

    private void schedule(int delayMs, Runnable action) {
        scheduledAction = action;
        clickTimer.stop();
        clickTimer.setInitialDelay(delayMs);
        clickTimer.setDelay(delayMs);
        clickTimer.restart();
    }

    private void runScheduledAction() {
        if (scheduledAction != null) scheduledAction.run();
    }

    private void pauseOrResume() {
        if (clickTimer == null) return;
        if (clickTimer.isRunning()) {
            clickTimer.stop();
            statusLabel.setForeground(ORANGE.darker());
            statusLabel.setText("已暂停 · 按 F5 继续，F6 停止");
            appendLog("任务暂停");
        } else {
            clickTimer.restart();
            statusLabel.setForeground(new Color(0, 135, 75));
            statusLabel.setText("运行中 · F5 暂停 · F6 立即停止");
            appendLog("任务继续");
        }
        refreshControls();
    }

    private void stopClicking(String reason) {
        if (clickTimer != null) {
            clickTimer.stop();
            clickTimer = null;
            scheduledAction = null;
        }
        statusLabel.setForeground(SUBTLE_TEXT);
        statusLabel.setText(reason + " · 本次 " + clickCount + " 次点击，" + completedTaskCycles + " 轮");
        appendLog("任务结束：" + reason + "，点击 " + clickCount + " 次，完成 " + completedTaskCycles + " 轮");
        refreshControls();
    }

    private void deleteSelectedPoint() {
        if (clickTimer != null) return;
        int index = pointList.getSelectedIndex();
        if (index < 0) { statusLabel.setText("请先选中要删除的点位"); return; }
        savedPoints.remove(index);
        saveQuietly();
        refreshInterface();
        if (!savedPoints.isEmpty()) pointList.setSelectedIndex(Math.min(index, savedPoints.size() - 1));
        statusLabel.setText("已删除点位 " + (index + 1));
        appendLog("删除点位 " + (index + 1));
    }

    private void moveSelectedPoint(int direction) {
        if (clickTimer != null) return;
        int index = pointList.getSelectedIndex();
        int targetIndex = index + direction;
        if (index < 0 || targetIndex < 0 || targetIndex >= savedPoints.size()) return;
        Collections.swap(savedPoints, index, targetIndex);
        saveQuietly();
        refreshInterface();
        pointList.setSelectedIndex(targetIndex);
        statusLabel.setText("已调整点位顺序");
        appendLog("移动点位顺序");
    }

    private void clearPoints() {
        if (clickTimer != null || savedPoints.isEmpty()) return;
        int choice = JOptionPane.showConfirmDialog(this, "确定清空全部 " + savedPoints.size() + " 个点位吗？", "清空点位", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;
        savedPoints.clear();
        saveQuietly();
        refreshInterface();
        statusLabel.setText("已清空全部点位");
        appendLog("清空全部点位");
    }

    private void importConfiguration() {
        if (clickTimer != null) return;
        JFileChooser chooser = configurationChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        loadConfiguration(chooser.getSelectedFile().toPath(), true);
        currentConfigFile = chooser.getSelectedFile().toPath();
        saveQuietly(); // 同时让下次启动恢复本次导入的配置。
        refreshInterface();
        statusLabel.setText("已导入点位配置");
        appendLog("导入配置：" + currentConfigFile);
    }

    private void exportConfiguration() { saveConfigurationToChooser(false); }
    private void saveConfigurationAs() { saveConfigurationToChooser(true); }

    private void saveConfigurationToChooser(boolean changeCurrentFile) {
        if (clickTimer != null) return;
        JFileChooser chooser = configurationChooser();
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path file = ensurePropertiesExtension(chooser.getSelectedFile().toPath());
        if (Files.exists(file) && JOptionPane.showConfirmDialog(this, "文件已存在，是否覆盖？", "确认覆盖", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            saveConfiguration(file);
            if (changeCurrentFile) currentConfigFile = file;
            statusLabel.setText("配置已保存");
            appendLog("保存配置：" + file);
        } catch (IOException exception) { showError("无法保存配置", exception); }
    }

    private void saveCurrentConfiguration() {
        try {
            saveConfiguration(currentConfigFile);
            // 始终同步一份默认配置，供下次打开软件自动恢复。
            if (!currentConfigFile.equals(defaultConfigFile)) saveConfiguration(defaultConfigFile);
            statusLabel.setText("当前配置已保存");
            appendLog("保存当前配置");
        } catch (IOException exception) { showError("无法保存当前配置", exception); }
    }

    private JFileChooser configurationChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("ClickFlow 配置 (*.properties)", "properties"));
        return chooser;
    }

    private Path ensurePropertiesExtension(Path file) {
        return file.getFileName().toString().toLowerCase().endsWith(".properties") ? file : Path.of(file + ".properties");
    }

    private void exportExecutionReport() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("CSV 报告 (*.csv)", "csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path file = chooser.getSelectedFile().toPath();
        if (!file.getFileName().toString().toLowerCase().endsWith(".csv")) file = Path.of(file + ".csv");
        String report = "项目,数值\n导出时间," + LocalDateTime.now().format(TIME_FORMAT) + "\n任务开始," + (taskStartedAt == null ? "未运行" : taskStartedAt.format(TIME_FORMAT)) + "\n点击次数," + clickCount + "\n完成循环," + completedTaskCycles + "\n配置点位数," + savedPoints.size() + "\n";
        try {
            Files.writeString(file, report, StandardCharsets.UTF_8);
            statusLabel.setText("任务执行报告已导出");
        } catch (IOException exception) { showError("无法导出报告", exception); }
    }

    private void showUsage() {
        JOptionPane.showMessageDialog(this, "1. 移动鼠标到目标位置，按 F3 添加点位。\n2. 双击点位，可设置该点位的延迟、间隔和点击次数。\n3. 在“设置 → 设置任务界面”修改新点位默认参数和任务循环次数。\n4. F4 启动，F5 暂停/继续，F6 立即停止。\n5. 配置会自动保存；也可在“文件”菜单导入、导出或另存为。", "使用说明", JOptionPane.INFORMATION_MESSAGE);
    }

    private void openLogFile() {
        try {
            Files.createDirectories(appDirectory);
            if (!Files.exists(logFile)) Files.createFile(logFile);
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(logFile.toFile());
            else statusLabel.setText("当前系统不支持直接打开日志文件");
        } catch (IOException exception) { showError("无法打开日志文件", exception); }
    }

    private void checkForUpdates() {
        JOptionPane.showMessageDialog(this, "当前为本地版本，尚未配置在线更新服务。", "检查更新", JOptionPane.INFORMATION_MESSAGE);
    }

    private void saveConfiguration(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Properties properties = new Properties();
        properties.setProperty("version", "1");
        properties.setProperty("default.startDelayMs", String.valueOf(defaultStartDelayMs));
        properties.setProperty("default.intervalMs", String.valueOf(defaultIntervalMs));
        properties.setProperty("default.clicks", String.valueOf(defaultClicks));
        properties.setProperty("task.cycleLimit", String.valueOf(taskCycleLimit));
        properties.setProperty("points.count", String.valueOf(savedPoints.size()));
        for (int i = 0; i < savedPoints.size(); i++) {
            ClickPoint point = savedPoints.get(i);
            String encodedName = Base64.getUrlEncoder().encodeToString(point.name.getBytes(StandardCharsets.UTF_8));
            String encodedHotkey = Base64.getUrlEncoder().encodeToString(point.hotkey.getBytes(StandardCharsets.UTF_8));
            properties.setProperty("point." + i, encodedName + "," + point.x + "," + point.y + "," + point.startDelayMs + "," + point.intervalMs + "," + point.clicks + "," + point.actionType + "," + point.wheelSteps + "," + encodedHotkey);
        }
        properties.setProperty("recording.count", String.valueOf(recordedActions.size()));
        for (int i = 0; i < recordedActions.size(); i++) {
            RecordedMouseAction action = recordedActions.get(i);
            properties.setProperty("recording." + i, action.type + "," + action.x + "," + action.y + "," + action.button + "," + action.delayMs + "," + action.keyCode);
        }
        try (OutputStream output = Files.newOutputStream(file)) { properties.store(output, "ClickFlow configuration"); }
    }

    private void loadConfiguration(Path file, boolean showErrors) {
        if (!Files.exists(file)) return;
        Properties properties = new Properties();
        try (var input = Files.newInputStream(file)) {
            properties.load(input);
            defaultStartDelayMs = positiveOrZero(properties, "default.startDelayMs", 0);
            defaultIntervalMs = atLeast(properties, "default.intervalMs", 100, 10);
            defaultClicks = atLeast(properties, "default.clicks", 1, 1);
            taskCycleLimit = positiveOrZero(properties, "task.cycleLimit", 0);
            savedPoints.clear();
            recordedActions.clear();
            int count = positiveOrZero(properties, "points.count", 0);
            for (int i = 0; i < count; i++) {
                String[] parts = properties.getProperty("point." + i, "").split(",");
                if (parts.length == 9) {
                    String name = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
                    String hotkey = new String(Base64.getUrlDecoder().decode(parts[8]), StandardCharsets.UTF_8);
                    savedPoints.add(new ClickPoint(name, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Math.max(0, Integer.parseInt(parts[3])), Math.max(10, Integer.parseInt(parts[4])), Math.max(1, Integer.parseInt(parts[5])), PointActionType.valueOf(parts[6]), Integer.parseInt(parts[7]), hotkey));
                } else if (parts.length == 6) {
                    String name = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
                    savedPoints.add(new ClickPoint(name, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Math.max(0, Integer.parseInt(parts[3])), Math.max(10, Integer.parseInt(parts[4])), Math.max(1, Integer.parseInt(parts[5]))));
                } else if (parts.length == 5) {
                    // 兼容旧版本未命名的配置文件。
                    savedPoints.add(new ClickPoint("点位 " + (i + 1), Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Math.max(0, Integer.parseInt(parts[2])), Math.max(10, Integer.parseInt(parts[3])), Math.max(1, Integer.parseInt(parts[4]))));
                }
            }
            int recordingCount = positiveOrZero(properties, "recording.count", 0);
            for (int i = 0; i < recordingCount; i++) {
                String[] parts = properties.getProperty("recording." + i, "").split(",");
                if (parts.length == 6) recordedActions.add(new RecordedMouseAction(RecordedActionType.valueOf(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), Math.max(0, Integer.parseInt(parts[4])), Integer.parseInt(parts[5])));
                else if (parts.length == 5) recordedActions.add(new RecordedMouseAction(RecordedActionType.valueOf(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), Math.max(0, Integer.parseInt(parts[4]))));
            }
        } catch (Exception exception) {
            if (showErrors) showError("无法读取配置", exception);
        }
    }

    private int positiveOrZero(Properties p, String key, int fallback) { try { return Math.max(0, Integer.parseInt(p.getProperty(key, String.valueOf(fallback)))); } catch (NumberFormatException e) { return fallback; } }
    private int atLeast(Properties p, String key, int fallback, int min) { try { return Math.max(min, Integer.parseInt(p.getProperty(key, String.valueOf(fallback)))); } catch (NumberFormatException e) { return fallback; } }
    private int intValue(JSpinner spinner) { return (Integer) spinner.getValue(); }
    private boolean isClicking() { return clickTimer != null && clickTimer.isRunning(); }

    private void refreshInterface() { refreshPointList(); refreshTaskSummary(); refreshRecordingSummary(); refreshControls(); }
    private void refreshPointList() {
        int selectedIndex = pointList.getSelectedIndex();
        pointListModel.clear();
        for (int i = 0; i < savedPoints.size(); i++) {
            ClickPoint p = savedPoints.get(i);
            pointListModel.addElement(String.format("%02d  ·  %s     (%d, %d)     %s · 延迟 %dms · 间隔 %dms · %d 次", i + 1, p.name, p.x, p.y, pointActionText(p), p.startDelayMs, p.intervalMs, p.clicks));
        }
        if (selectedIndex >= 0 && selectedIndex < savedPoints.size()) pointList.setSelectedIndex(selectedIndex);
    }
    private String pointActionText(ClickPoint point) {
        return switch (point.actionType) {
            case LEFT_CLICK -> "左键";
            case RIGHT_CLICK -> "右键";
            case WHEEL -> "滚轮 " + point.wheelSteps + " 步";
            case HOTKEY -> "快捷键 " + (point.hotkey.isBlank() ? "未设置" : point.hotkey);
        };
    }
    private void refreshTaskSummary() {
        String cycleText = taskCycleLimit == 0 ? "无限循环" : taskCycleLimit + " 轮";
        taskSummaryLabel.setText("共 " + savedPoints.size() + " 个点位 · 任务循环：" + cycleText + " · 设置中可调整新点位默认参数");
    }
    private void refreshRecordingSummary() {
        if (recording) recordingSummaryLabel.setText("正在录制 · 已捕获 " + recordedActions.size() + " 个动作");
        else recordingSummaryLabel.setText(recordedActions.isEmpty() ? "未录制鼠标操作" : "已保存 " + recordedActions.size() + " 个鼠标动作，可选择“录制回放”执行");
    }
    private void refreshControls() {
        boolean exists = clickTimer != null;
        startButton.setEnabled(!exists);
        pauseButton.setEnabled(exists);
        pauseButton.setText(isClicking() ? "暂停" : "继续");
        stopButton.setEnabled(exists);
        deleteButton.setEnabled(!exists && pointList.getSelectedIndex() >= 0);
        int selectedIndex = pointList.getSelectedIndex();
        moveUpButton.setEnabled(!exists && selectedIndex > 0);
        moveDownButton.setEnabled(!exists && selectedIndex >= 0 && selectedIndex < savedPoints.size() - 1);
        clearButton.setEnabled(!exists && !savedPoints.isEmpty());
        recordButton.setEnabled(!exists);
        clearRecordingButton.setEnabled(!exists && !recording && !recordedActions.isEmpty());
        taskModeBox.setEnabled(!exists && !recording);
        pointList.setEnabled(!exists);
    }

    private Image loadAppIcon() {
        var url = AutoClickerApp.class.getResource("/icons/clickflow-icon.png");
        return url == null ? null : new ImageIcon(url).getImage();
    }

    private void installResizeSupport() {
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (!(event instanceof MouseEvent mouseEvent) || !isShowing()) return;
            Object source = mouseEvent.getSource();
            if (!(source instanceof java.awt.Component component) || !SwingUtilities.isDescendingFrom(component, getRootPane())) return;
            if (mouseEvent.getID() == MouseEvent.MOUSE_MOVED) updateResizeCursor(mouseEvent);
            if (mouseEvent.getID() == MouseEvent.MOUSE_PRESSED) beginResize(mouseEvent);
            if (mouseEvent.getID() == MouseEvent.MOUSE_DRAGGED) resizeWindow(mouseEvent);
            if (mouseEvent.getID() == MouseEvent.MOUSE_RELEASED) { resizeScreenAnchor = null; resizeBoundsAnchor = null; }
        }, AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
    }

    private void updateResizeCursor(MouseEvent event) {
        int x = event.getXOnScreen() - getX();
        int y = event.getYOnScreen() - getY();
        int margin = 7;
        boolean left = x <= margin, right = x >= getWidth() - margin, top = y <= margin, bottom = y >= getHeight() - margin;
        resizeCursorType = top && left ? Cursor.NW_RESIZE_CURSOR : top && right ? Cursor.NE_RESIZE_CURSOR : bottom && left ? Cursor.SW_RESIZE_CURSOR : bottom && right ? Cursor.SE_RESIZE_CURSOR : left ? Cursor.W_RESIZE_CURSOR : right ? Cursor.E_RESIZE_CURSOR : top ? Cursor.N_RESIZE_CURSOR : bottom ? Cursor.S_RESIZE_CURSOR : Cursor.DEFAULT_CURSOR;
        setCursor(Cursor.getPredefinedCursor(resizeCursorType));
    }

    private void beginResize(MouseEvent event) {
        updateResizeCursor(event);
        if (resizeCursorType == Cursor.DEFAULT_CURSOR) return;
        resizeScreenAnchor = event.getLocationOnScreen();
        resizeBoundsAnchor = getBounds();
    }

    private void resizeWindow(MouseEvent event) {
        if (resizeScreenAnchor == null || resizeBoundsAnchor == null || resizeCursorType == Cursor.DEFAULT_CURSOR) return;
        int dx = event.getXOnScreen() - resizeScreenAnchor.x, dy = event.getYOnScreen() - resizeScreenAnchor.y;
        Rectangle next = new Rectangle(resizeBoundsAnchor);
        if (resizeCursorType == Cursor.W_RESIZE_CURSOR || resizeCursorType == Cursor.NW_RESIZE_CURSOR || resizeCursorType == Cursor.SW_RESIZE_CURSOR) { next.x += dx; next.width -= dx; }
        if (resizeCursorType == Cursor.E_RESIZE_CURSOR || resizeCursorType == Cursor.NE_RESIZE_CURSOR || resizeCursorType == Cursor.SE_RESIZE_CURSOR) next.width += dx;
        if (resizeCursorType == Cursor.N_RESIZE_CURSOR || resizeCursorType == Cursor.NW_RESIZE_CURSOR || resizeCursorType == Cursor.NE_RESIZE_CURSOR) { next.y += dy; next.height -= dy; }
        if (resizeCursorType == Cursor.S_RESIZE_CURSOR || resizeCursorType == Cursor.SW_RESIZE_CURSOR || resizeCursorType == Cursor.SE_RESIZE_CURSOR) next.height += dy;
        Dimension minimum = getMinimumSize();
        if (next.width < minimum.width) { if (next.x != resizeBoundsAnchor.x) next.x = resizeBoundsAnchor.x + resizeBoundsAnchor.width - minimum.width; next.width = minimum.width; }
        if (next.height < minimum.height) { if (next.y != resizeBoundsAnchor.y) next.y = resizeBoundsAnchor.y + resizeBoundsAnchor.height - minimum.height; next.height = minimum.height; }
        setBounds(next);
    }
    private void saveQuietly() { try { saveConfiguration(defaultConfigFile); } catch (IOException exception) { appendLog("自动保存失败：" + exception.getMessage()); } }
    private void appendLog(String text) { try { Files.createDirectories(appDirectory); Files.writeString(logFile, "[" + LocalDateTime.now().format(TIME_FORMAT) + "] " + text + System.lineSeparator(), StandardCharsets.UTF_8, Files.exists(logFile) ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE); } catch (IOException ignored) { } }
    private void showError(String title, Exception exception) { appendLog(title + "：" + exception.getMessage()); JOptionPane.showMessageDialog(this, exception.getMessage(), title, JOptionPane.ERROR_MESSAGE); }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { new AutoClickerApp().setVisible(true); }
            catch (AWTException exception) { JOptionPane.showMessageDialog(null, "无法创建鼠标控制器：" + exception.getMessage(), "启动失败", JOptionPane.ERROR_MESSAGE); }
        });
    }

    private static class ClickPoint {
        private String name;
        private final int x, y;
        private int startDelayMs, intervalMs, clicks;
        private PointActionType actionType;
        private int wheelSteps;
        private String hotkey;
        private ClickPoint(String name, int x, int y, int startDelayMs, int intervalMs, int clicks) { this(name, x, y, startDelayMs, intervalMs, clicks, PointActionType.LEFT_CLICK, 1, ""); }
        private ClickPoint(String name, int x, int y, int startDelayMs, int intervalMs, int clicks, PointActionType actionType, int wheelSteps, String hotkey) { this.name = name; this.x = x; this.y = y; this.startDelayMs = startDelayMs; this.intervalMs = intervalMs; this.clicks = clicks; this.actionType = actionType; this.wheelSteps = wheelSteps; this.hotkey = hotkey; }
    }
    private static class HotkeyCaptureField extends JTextField {
        private final LinkedHashSet<Integer> pressedKeys = new LinkedHashSet<>();
        private String hotkey;
        HotkeyCaptureField(String hotkey) {
            super(hotkey, 18);
            this.hotkey = hotkey == null ? "" : hotkey;
            setToolTipText("点击后按下组合键，例如按住 Ctrl 再按 C");
            addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent event) {
                    event.consume();
                    pressedKeys.add(event.getKeyCode());
                    if (!isModifier(event.getKeyCode())) {
                        HotkeyCaptureField.this.hotkey = formatHotkey(pressedKeys);
                        setText(HotkeyCaptureField.this.hotkey);
                    }
                }
                @Override public void keyReleased(KeyEvent event) { pressedKeys.remove(event.getKeyCode()); }
                @Override public void keyTyped(KeyEvent event) { event.consume(); }
            });
        }
        String getHotkey() { return hotkey; }
        private boolean isModifier(int keyCode) { return keyCode == KeyEvent.VK_CONTROL || keyCode == KeyEvent.VK_ALT || keyCode == KeyEvent.VK_SHIFT || keyCode == KeyEvent.VK_WINDOWS || keyCode == KeyEvent.VK_META; }
        private String formatHotkey(LinkedHashSet<Integer> keys) {
            List<String> labels = new ArrayList<>();
            if (keys.contains(KeyEvent.VK_CONTROL)) labels.add("Ctrl");
            if (keys.contains(KeyEvent.VK_ALT)) labels.add("Alt");
            if (keys.contains(KeyEvent.VK_SHIFT)) labels.add("Shift");
            if (keys.contains(KeyEvent.VK_WINDOWS) || keys.contains(KeyEvent.VK_META)) labels.add("Win");
            for (int key : keys) if (!isModifier(key)) labels.add(KeyEvent.getKeyText(key));
            return String.join("+", labels);
        }
    }
    private enum PointActionType { LEFT_CLICK, RIGHT_CLICK, WHEEL, HOTKEY }
    private class MacWindowControls extends JPanel {
        MacWindowControls() {
            super(new FlowLayout(FlowLayout.RIGHT, 7, 0));
            setOpaque(false);
            add(new MacCircleButton(new Color(255, 95, 86), "×", () -> AutoClickerApp.this.dispatchEvent(new WindowEvent(AutoClickerApp.this, WindowEvent.WINDOW_CLOSING))));
            add(new MacCircleButton(new Color(255, 189, 46), "−", () -> setState(JFrame.ICONIFIED)));
            add(new MacCircleButton(new Color(39, 201, 63), "+", () -> setExtendedState(getExtendedState() == JFrame.MAXIMIZED_BOTH ? JFrame.NORMAL : JFrame.MAXIMIZED_BOTH)));
        }
    }
    private static class MacCircleButton extends JButton {
        private final Color color; private final String glyph;
        MacCircleButton(Color color, String glyph, Runnable action) {
            this.color = color; this.glyph = glyph;
            setPreferredSize(new Dimension(15, 15)); setBorderPainted(false); setFocusPainted(false); setContentAreaFilled(false);
            addActionListener(event -> action.run());
        }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color); g2.fillOval(1, 1, 13, 13);
            if (getModel().isRollover()) { g2.setColor(new Color(70, 70, 70)); g2.setFont(new Font("Arial", Font.BOLD, 10)); g2.drawString(glyph, 5, 11); }
            g2.dispose();
        }
    }
    private class DragWindowAdapter extends MouseAdapter {
        private Point screenAnchor;
        private Point windowAnchor;
        @Override public void mousePressed(MouseEvent event) {
            screenAnchor = event.getLocationOnScreen();
            windowAnchor = AutoClickerApp.this.getLocation();
        }
        @Override public void mouseDragged(MouseEvent event) {
            if (screenAnchor == null || windowAnchor == null) return;
            Point point = event.getLocationOnScreen();
            AutoClickerApp.this.setLocation(windowAnchor.x + point.x - screenAnchor.x, windowAnchor.y + point.y - screenAnchor.y);
        }
    }
    private static class MacComboBoxUI extends BasicComboBoxUI {
        @Override protected JButton createArrowButton() {
            JButton button = new JButton("⌄");
            button.setFont(new Font("Arial", Font.BOLD, 13));
            button.setForeground(new Color(75, 80, 90));
            button.setBorderPainted(false); button.setFocusPainted(false); button.setContentAreaFilled(false);
            return button;
        }
    }
    private enum RecordedActionType { MOVE, PRESS, RELEASE, KEY_PRESS, KEY_RELEASE }
    private static class RecordedMouseAction {
        private final RecordedActionType type;
        private final int x, y, button, delayMs, keyCode;
        private RecordedMouseAction(RecordedActionType type, int x, int y, int button, int delayMs) { this(type, x, y, button, delayMs, 0); }
        private RecordedMouseAction(RecordedActionType type, int x, int y, int button, int delayMs, int keyCode) { this.type = type; this.x = x; this.y = y; this.button = button; this.delayMs = delayMs; this.keyCode = keyCode; }
    }
    private static class TrafficLightPanel extends JPanel {
        TrafficLightPanel() { setOpaque(false); setPreferredSize(new Dimension(58, 18)); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color[] colors = {new Color(255, 95, 86), new Color(255, 189, 46), new Color(39, 201, 63)};
            for (int i = 0; i < colors.length; i++) { g2.setColor(colors[i]); g2.fillOval(i * 18, 3, 12, 12); }
            g2.dispose();
        }
    }
    private static class MacPointRenderer extends DefaultListCellRenderer {
        @Override public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, false, false);
            label.setOpaque(true);
            label.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            label.setForeground(TEXT);
            label.setBackground(selected ? new Color(220, 238, 255) : CARD);
            return label;
        }
    }
    private static class GlassBackgroundPanel extends JPanel {
        GlassBackgroundPanel() { setOpaque(true); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
        }
    }
    private static class RoundPanel extends JPanel {
        private final Color color; private final int radius;
        RoundPanel(Color color, int radius) { this.color = color; this.radius = radius; setOpaque(false); }
        @Override protected void paintComponent(Graphics graphics) { Graphics2D g2 = (Graphics2D) graphics.create(); g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); g2.setColor(color); g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius); g2.dispose(); super.paintComponent(graphics); }
    }
    private static class RoundButton extends JButton {
        private final Color color;
        RoundButton(String text, Color color) { super(text); this.color = color; setFont(new Font("Microsoft YaHei UI", Font.BOLD, 13)); setForeground(Color.WHITE); setBorderPainted(false); setFocusPainted(false); setContentAreaFilled(false); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); setPreferredSize(new Dimension(104, 38)); }
        @Override protected void paintComponent(Graphics graphics) { Graphics2D g2 = (Graphics2D) graphics.create(); g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); Color fill = isEnabled() ? color : new Color(210, 213, 220); if (getModel().isPressed()) fill = fill.darker(); g2.setColor(fill); g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18); g2.dispose(); super.paintComponent(graphics); }
    }
}
