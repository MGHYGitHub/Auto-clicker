package com.example.autoclicker;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
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
import java.awt.AWTException;
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
import java.awt.Robot;
import java.awt.event.InputEvent;
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
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/** ClickFlow：支持独立点位参数、配置持久化与全局快捷键的桌面连点器。 */
public class AutoClickerApp extends JFrame implements NativeKeyListener {
    private static final Color BACKGROUND = new Color(246, 247, 251);
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

    public AutoClickerApp() throws AWTException {
        super("ClickFlow · 自动化连点器");
        robot = new Robot();
        robot.setAutoDelay(0);
        loadConfiguration(defaultConfigFile, false);
        createInterface();
        setIconImage(loadAppIcon());
        bindActions();
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
        JLabel title = new JLabel("ClickFlow");
        title.setForeground(TEXT);
        title.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 30));
        JLabel subtitle = new JLabel("按 F3 记录坐标 · 双击点位修改独立参数");
        subtitle.setForeground(SUBTLE_TEXT);
        subtitle.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
        header.add(title, BorderLayout.NORTH);
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
        content.add(mainCard, BorderLayout.CENTER);

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
        c.gridy = 2; c.insets = new Insets(0, 0, 0, 0);
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
            try { GlobalScreen.unregisterNativeHook(); } catch (Exception ignored) { }
        }
    }

    @Override public void nativeKeyPressed(NativeKeyEvent event) {
        switch (event.getKeyCode()) {
            case NativeKeyEvent.VC_F3 -> SwingUtilities.invokeLater(this::saveMousePosition);
            case NativeKeyEvent.VC_F4 -> SwingUtilities.invokeLater(this::startClicking);
            case NativeKeyEvent.VC_F5 -> SwingUtilities.invokeLater(this::pauseOrResume);
            case NativeKeyEvent.VC_F6 -> SwingUtilities.invokeLater(() -> stopClicking("快捷键停止"));
            default -> { }
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
        int choice = JOptionPane.showConfirmDialog(this, panel, "设置任务", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
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
        JSpinner startDelay = spinner(point.startDelayMs, 0, 60_000, 50);
        JSpinner interval = spinner(point.intervalMs, 10, 60_000, 10);
        JSpinner clicks = spinner(point.clicks, 1, 100_000, 1);
        JPanel fields = dialogFields();
        addDialogRow(fields, 0, "点位名称", nameField, "");
        addDialogRow(fields, 1, "开始前延迟", startDelay, "毫秒");
        addDialogRow(fields, 2, "连续点击间隔", interval, "毫秒");
        addDialogRow(fields, 3, "此点位点击次数", clicks, "次");
        int choice = JOptionPane.showConfirmDialog(this, fields, "编辑点位 " + (index + 1) + "  ·  (" + point.x + ", " + point.y + ")", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            point.name = name.isEmpty() ? "点位 " + (index + 1) : name;
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
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
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
            properties.setProperty("point." + i, encodedName + "," + point.x + "," + point.y + "," + point.startDelayMs + "," + point.intervalMs + "," + point.clicks);
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
            int count = positiveOrZero(properties, "points.count", 0);
            for (int i = 0; i < count; i++) {
                String[] parts = properties.getProperty("point." + i, "").split(",");
                if (parts.length == 6) {
                    String name = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
                    savedPoints.add(new ClickPoint(name, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Math.max(0, Integer.parseInt(parts[3])), Math.max(10, Integer.parseInt(parts[4])), Math.max(1, Integer.parseInt(parts[5]))));
                } else if (parts.length == 5) {
                    // 兼容旧版本未命名的配置文件。
                    savedPoints.add(new ClickPoint("点位 " + (i + 1), Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Math.max(0, Integer.parseInt(parts[2])), Math.max(10, Integer.parseInt(parts[3])), Math.max(1, Integer.parseInt(parts[4]))));
                }
            }
        } catch (Exception exception) {
            if (showErrors) showError("无法读取配置", exception);
        }
    }

    private int positiveOrZero(Properties p, String key, int fallback) { try { return Math.max(0, Integer.parseInt(p.getProperty(key, String.valueOf(fallback)))); } catch (NumberFormatException e) { return fallback; } }
    private int atLeast(Properties p, String key, int fallback, int min) { try { return Math.max(min, Integer.parseInt(p.getProperty(key, String.valueOf(fallback)))); } catch (NumberFormatException e) { return fallback; } }
    private int intValue(JSpinner spinner) { return (Integer) spinner.getValue(); }
    private boolean isClicking() { return clickTimer != null && clickTimer.isRunning(); }

    private void refreshInterface() { refreshPointList(); refreshTaskSummary(); refreshControls(); }
    private void refreshPointList() {
        int selectedIndex = pointList.getSelectedIndex();
        pointListModel.clear();
        for (int i = 0; i < savedPoints.size(); i++) {
            ClickPoint p = savedPoints.get(i);
            pointListModel.addElement(String.format("%02d  ·  %s     (%d, %d)     延迟 %dms · 间隔 %dms · %d 次", i + 1, p.name, p.x, p.y, p.startDelayMs, p.intervalMs, p.clicks));
        }
        if (selectedIndex >= 0 && selectedIndex < savedPoints.size()) pointList.setSelectedIndex(selectedIndex);
    }
    private void refreshTaskSummary() {
        String cycleText = taskCycleLimit == 0 ? "无限循环" : taskCycleLimit + " 轮";
        taskSummaryLabel.setText("共 " + savedPoints.size() + " 个点位 · 任务循环：" + cycleText + " · 设置中可调整新点位默认参数");
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
        pointList.setEnabled(!exists);
    }

    private Image loadAppIcon() {
        var url = AutoClickerApp.class.getResource("/icons/clickflow-icon.png");
        return url == null ? null : new ImageIcon(url).getImage();
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
        private ClickPoint(String name, int x, int y, int startDelayMs, int intervalMs, int clicks) { this.name = name; this.x = x; this.y = y; this.startDelayMs = startDelayMs; this.intervalMs = intervalMs; this.clicks = clicks; }
    }
    private static class GlassBackgroundPanel extends JPanel {
        GlassBackgroundPanel() { setOpaque(false); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setPaint(new GradientPaint(0, 0, new Color(236, 244, 255), getWidth(), getHeight(), new Color(244, 238, 255)));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
            super.paintComponent(graphics);
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
