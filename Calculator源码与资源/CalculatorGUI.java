import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.regex.*;

/**
 * Calculator GUI —— 融合版:参考 v1.5.9 布局(输入框+输出区+变量区)+ 科学计算器按钮键盘
 * 深色主题、中文、纯鼠标优先(输入框兼容键盘)。
 */
public class CalculatorGUI {

    private static final Color BG_WIN     = new Color(0x12, 0x12, 0x1C);
    private static final Color BG_TITLE   = new Color(0x00, 0x00, 0x20);
    private static final Color BG_DISP    = new Color(0x0A, 0x0A, 0x14);
    private static final Color FG_DISP    = new Color(0xE8, 0xE0, 0xC8);
    private static final Color BORDER     = new Color(0x2A, 0x2A, 0x3A);
    private static final Color BG_NUM     = new Color(0x1E, 0x1E, 0x30);
    private static final Color FG_NUM     = Color.WHITE;
    private static final Color BG_OP      = new Color(0x26, 0x26, 0x3A);
    private static final Color FG_OP      = new Color(0xE0, 0xC0, 0x80);
    private static final Color BG_C       = new Color(0x4A, 0x15, 0x15);
    private static final Color FG_C       = new Color(0xFF, 0x88, 0x88);
    private static final Color BG_BS      = new Color(0x4A, 0x2E, 0x10);
    private static final Color FG_BS      = new Color(0xFF, 0xB0, 0x60);
    private static final Color BG_EQ      = new Color(0xD4, 0xA9, 0x4E);
    private static final Color FG_EQ      = new Color(0x10, 0x10, 0x18);
    private static final Color BG_FUNC    = new Color(0x10, 0x24, 0x2E);
    private static final Color FG_FUNC    = new Color(0xA8, 0xE8, 0xC8);
    private static final Color BG_CONST   = new Color(0x12, 0x31, 0x3B);
    private static final Color FG_CONST   = new Color(0x7F, 0xD4, 0xE8);
    private static final Color FG_TIP     = new Color(0x80, 0x80, 0x90);
    private static final Color BG_ERR     = new Color(0xFF, 0x88, 0x88);

    private JTextField inputField;   // 表达式输入(按钮插入/键盘编辑)
    private JTextArea outputArea;    // 计算输出历史
    private JTextArea varArea;       // 变量 / 任务
    private boolean justEvaluated = false;
    private String lastResult = null;
    private final java.util.List<String> history = new ArrayList<>();
    private int historyIndex = -1;
    private JFrame frame;

    public CalculatorGUI() {
        redirectStdout();
        initEngine();
        createUI();
    }

    private void redirectStdout() {
        // 必须指定 UTF-8 编码 + 按字节积累后整体解码,否则多字节字符(π ∑ ∏ 等)会被逐字节拆碎成乱码
        PrintStream guiStream = new PrintStream(new OutputStream() {
            private final java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            @Override public void write(int b) {
                buf.write(b);
            }
            @Override public void write(byte[] data, int off, int len) {
                buf.write(data, off, len);
            }
            @Override public void flush() {
                String text = buf.toString(java.nio.charset.StandardCharsets.UTF_8);
                buf.reset();
                if (text.isEmpty()) return;
                for (String raw : text.split("\n", -1)) {
                    String line = raw.trim();
                    if (line.isEmpty()) continue;
                    if (outputArea == null) { originalOut.println(line); continue; }
                    SwingUtilities.invokeLater(() -> routeOutput(line));
                }
            }
        }, true, java.nio.charset.StandardCharsets.UTF_8);
        System.setOut(guiStream);
        System.setErr(guiStream);
    }

    /** 输出分流:变量/任务信息 → 变量区;结果 → 大屏+历史;其余 → 历史 */
    private void routeOutput(String line) {
        if (line.startsWith("Defined:") || line.startsWith("Tasks:") || line.startsWith(" *")
                || line.startsWith("Variable '") || line.startsWith("(no") || line.startsWith("No vars")) {
            appendLine(varArea, line);
            return;
        }
        Matcher m = Pattern.compile("^\\[.*?\\]\\s*Result:\\s*(.*)$").matcher(line);
        if (m.find()) {
            showResult(m.group(1));
            appendLine(outputArea, line);
        } else {
            appendLine(outputArea, line);
        }
    }

    private static void appendLine(JTextArea area, String line) {
        area.append(line + "\n");
        area.setCaretPosition(area.getDocument().getLength());
    }

    private void initEngine() {
        try {
            Method md = calculator.class.getDeclaredMethod("initTerminalAndReader");
            md.setAccessible(true);
            md.invoke(null);
        } catch (Throwable t) { }
    }

    private void createUI() {
        frame = new JFrame("Calculator \u8ba1\u7b97\u5668");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setMinimumSize(new Dimension(720, 500));
        frame.getContentPane().setBackground(BG_WIN);
        // 窗口图标:ImageIO 同步加载 PNG(ImageIcon 对 ICO 不可靠)
        Image icon = null;
        try {
            java.io.InputStream is = getClass().getResourceAsStream("calculator-gui.png");
            if (is != null) icon = javax.imageio.ImageIO.read(is);
        } catch (Throwable t) { }
        if (icon != null) frame.setIconImage(icon);

        // 标题栏
        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setBackground(BG_TITLE);
        titlePanel.setBorder(new EmptyBorder(6, 14, 6, 14));
        JLabel title = new JLabel("Calculator \u8ba1\u7b97\u5668 \u00b7 \u7b26\u53f7\u8ba1\u7b97 + \u57df\u7cfb\u7edf");
        title.setForeground(FG_OP);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        titlePanel.add(title, BorderLayout.WEST);

        // 输入行:表达式输入框(无计算按钮,回车计算)
        inputField = new JTextField();
        inputField.setFont(new Font("Microsoft YaHei", Font.PLAIN, 16));
        inputField.setBackground(new Color(0x16, 0x16, 0x22));
        inputField.setForeground(FG_DISP);
        inputField.setCaretColor(FG_OP);
        inputField.setBorder(BorderFactory.createLineBorder(BORDER));
        inputField.addActionListener(e -> evaluate());
        // 输入框历史
        inputField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_UP) navHistory(-1);
                else if (e.getKeyCode() == KeyEvent.VK_DOWN) navHistory(1);
            }
        });
        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.setBorder(new EmptyBorder(6, 12, 4, 12));
        inputRow.setOpaque(false);
        inputRow.add(inputField, BorderLayout.CENTER);

        // 输出区 + 变量区(左右分栏)
        outputArea = new JTextArea();
        outputArea.setEditable(false);
        // 微软雅黑:完整支持 π ∑ ∏ ∪ ∩ ± 等符号(Consolas 缺字形会乱码)
        outputArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        outputArea.setBackground(BG_DISP);
        outputArea.setForeground(FG_DISP);
        outputArea.setBorder(new EmptyBorder(6, 8, 6, 8));
        JScrollPane outScroll = new JScrollPane(outputArea);
        outScroll.setBorder(BorderFactory.createTitledBorder("\u8ba1\u7b97\u8f93\u51fa"));

        varArea = new JTextArea();
        varArea.setEditable(false);
        varArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        varArea.setBackground(new Color(0x0E, 0x12, 0x1E));
        varArea.setForeground(FG_FUNC);
        varArea.setBorder(new EmptyBorder(6, 8, 6, 8));
        JScrollPane varScroll = new JScrollPane(varArea);
        varScroll.setBorder(BorderFactory.createTitledBorder("\u53d8\u91cf / \u4efb\u52a1"));
        varScroll.setPreferredSize(new Dimension(200, 0));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, outScroll, varScroll);
        split.setResizeWeight(0.7);
        split.setBorder(new EmptyBorder(4, 12, 4, 12));
        split.setBackground(BG_WIN);

        // 键盘区(功能列 + 主键盘)
        JPanel keyPanel = buildKeyPanel();

        // 命令按钮行(平分窗口宽)
        String[] cmds = {"\u5b9a\u4e49\u53d8\u91cf", "\u65b0\u4efb\u52a1", "\u4efb\u52a1\u5217\u8868",
                "\u4e0a\u4e00\u6761", "\u4e0b\u4e00\u6761", "\u6e05\u7a7a\u8f93\u51fa",
                "\u53d8\u91cf\u5237\u65b0", "\u5173\u4e8e", "\u9000\u51fa"};
        JPanel cmdPanel = new JPanel(new GridLayout(1, cmds.length, 2, 0));
        cmdPanel.setBorder(new EmptyBorder(0, 10, 6, 10));
        cmdPanel.setOpaque(false);
        for (String c : cmds) {
            JButton b = new JButton(c);
            b.setFont(b.getFont().deriveFont(Font.PLAIN, 12f));
            b.setBackground(BG_CONST); b.setForeground(FG_CONST);
            b.setFocusable(false);
            switch (c) {
                case "\u5b9a\u4e49\u53d8\u91cf" -> b.addActionListener(e -> { inputField.setText("|defineV "); inputField.requestFocusInWindow(); });
                case "\u65b0\u4efb\u52a1" -> b.addActionListener(e -> { inputField.setText("|newTask "); inputField.requestFocusInWindow(); });
                case "\u4efb\u52a1\u5217\u8868" -> b.addActionListener(e -> runCommand("listtasks"));
                case "\u4e0a\u4e00\u6761" -> b.addActionListener(e -> navHistory(-1));
                case "\u4e0b\u4e00\u6761" -> b.addActionListener(e -> navHistory(1));
                case "\u6e05\u7a7a\u8f93\u51fa" -> b.addActionListener(e -> outputArea.setText(""));
                case "\u53d8\u91cf\u5237\u65b0" -> b.addActionListener(e -> { varArea.setText(""); runCommand("definev"); });
                case "\u5173\u4e8e" -> b.addActionListener(e -> showAbout());
                default -> b.addActionListener(e -> System.exit(0));
            }
            cmdPanel.add(b);
        }

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(inputRow, BorderLayout.NORTH);
        center.add(split, BorderLayout.CENTER);
        center.add(keyPanel, BorderLayout.SOUTH);

        frame.add(titlePanel, BorderLayout.NORTH);
        frame.add(center, BorderLayout.CENTER);
        frame.add(cmdPanel, BorderLayout.SOUTH);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        inputField.requestFocusInWindow();
    }

    private JPanel buildKeyPanel() {
        JPanel keyPanel = new JPanel(new GridBagLayout());
        keyPanel.setBorder(new EmptyBorder(6, 10, 4, 10));
        keyPanel.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.BOTH;
        g.weightx = 1; g.weighty = 1;
        g.insets = new Insets(2, 2, 2, 2);

        String[] funcLabels = {"\u6781\u9650", "\u6c42\u548c", "\u4e58\u79ef", "\u9636\u4e58",
                               "\u6b63\u5f26", "\u4f59\u5f26", "\u6b63\u5207", "\u5bf9\u6570",
                               "\u53cd\u5f26", "\u53cd\u4f59", "\u5e2e\u52a9", "\u7248\u672c"};
        String[] funcIns = {"lim{", "\u2211{", "\u220f{", "!", "sin(", "cos(", "tan(", "log(",
                            "arcsin(", "arccos(", "", ""};
        for (int i = 0; i < funcLabels.length; i++) {
            g.gridx = i % 2; g.gridy = i / 2;
            boolean isHelper = i >= 10;
            JButton b = mkButton(funcLabels[i], isHelper ? BG_CONST : BG_FUNC,
                    isHelper ? FG_CONST : FG_FUNC, 12f);
            final String lbl = funcLabels[i], ins = funcIns[i];
            if (lbl.equals("\u5e2e\u52a9")) b.addActionListener(e -> showHelp());
            else if (lbl.equals("\u7248\u672c")) b.addActionListener(e -> showAbout());
            else b.addActionListener(e -> funcClick(lbl, ins));
            keyPanel.add(b, g);
        }

        String[][] mainKeys = {
            {"C", "CE", "\u232b", "(", ")"},
            {"x\u00b2", "\u221ax", "1/x", "^", "%"},
            {"7", "8", "9", "\u00f7", "e"},
            {"4", "5", "6", "\u00d7", null},
            {"1", "2", "3", "-", null},
            {"0", ".", "\u03c0", "+", null},
        };
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 5; col++) {
                String key = mainKeys[row][col];
                if (key == null) continue;
                g.gridx = col + 2; g.gridy = row;
                g.gridwidth = 1; g.gridheight = 1;
                JButton b = mkButton(key, BG_NUM, FG_NUM, 14f);
                if (key.equals("C") || key.equals("CE")) {
                    b.setBackground(BG_C); b.setForeground(FG_C);
                    b.addActionListener(e -> { inputField.setText(""); justEvaluated = false; });
                } else if (key.equals("\u232b")) {
                    b.setBackground(BG_BS); b.setForeground(FG_BS);
                    b.addActionListener(e -> backspace());
                } else if (key.equals("x\u00b2") || key.equals("\u221ax") || key.equals("1/x")) {
                    b.setBackground(BG_OP); b.setForeground(FG_OP);
                    final String k = key;
                    b.addActionListener(e -> unaryNow(k));
                } else if (key.equals("e") || key.equals("\u03c0")) {
                    b.setBackground(BG_CONST); b.setForeground(FG_CONST);
                    b.addActionListener(e -> insert(key));
                } else if (key.matches("[()^%]") || key.equals("\u00f7") || key.equals("\u00d7") || key.equals("-") || key.equals("+")) {
                    b.setBackground(BG_OP); b.setForeground(FG_OP);
                    b.addActionListener(e -> insert(key));
                } else {
                    b.addActionListener(e -> insert(key));
                }
                keyPanel.add(b, g);
            }
        }

        // = 大按钮(右侧底部,占 3 行高)
        g.gridx = 6; g.gridy = 3; g.gridwidth = 1; g.gridheight = 3;
        JButton eqBtn = mkButton("=", BG_EQ, FG_EQ, 20f);
        eqBtn.addActionListener(e -> evaluate());
        keyPanel.add(eqBtn, g);
        return keyPanel;
    }

    private JButton mkButton(String label, Color bg, Color fg, float fontSize) {
        JButton b = new JButton(label);
        b.setFont(b.getFont().deriveFont(Font.BOLD, fontSize));
        b.setBackground(bg);
        b.setForeground(fg);
        b.setFocusable(false);
        return b;
    }

    // ---------- 输入与计算 ----------

    private void funcClick(String label, String insert) {
        if (label.equals("\u6c42\u548c")) { sumOrProd(true); return; }
        if (label.equals("\u4e58\u79ef")) { sumOrProd(false); return; }
        if (label.equals("\u6781\u9650")) { limitDialog(); return; }
        insert(insert);
    }

    private void insert(String s) {
        if (justEvaluated) { inputField.setText(""); justEvaluated = false; }
        // 界面符号 → 引擎语法
        if (s.equals("\u00f7")) s = "/";
        else if (s.equals("\u00d7")) s = "*";
        String t = inputField.getText();
        int pos = inputField.getCaretPosition();
        inputField.setText(t.substring(0, pos) + s + t.substring(pos));
        inputField.setCaretPosition(pos + s.length());
        inputField.requestFocusInWindow();
        justEvaluated = false;
    }

    private void backspace() {
        String t = inputField.getText();
        if (t.isEmpty()) return;
        int pos = inputField.getCaretPosition();
        if (pos <= 0) return;
        inputField.setText(t.substring(0, pos - 1) + t.substring(pos));
        inputField.setCaretPosition(pos - 1);
        justEvaluated = false;
    }

    private String evalNow(String expr) {
        try {
            double v = calculator.evaluateNumeric(convertSci(expr));
            return Double.isNaN(v) ? null : fmt(v);
        } catch (Throwable t) { return null; }
    }

    private void unaryNow(String key) {
        String t = inputField.getText();
        if (t.isEmpty()) return;
        String wrapped = switch (key) {
            case "x\u00b2" -> "(" + t + ")^2";
            case "\u221ax" -> "(" + t + ")^(1/2)";
            default -> "1/(" + t + ")";
        };
        String v = evalNow(wrapped);
        if (v != null) {
            appendLine(outputArea, "[" + taskName() + "] Result: " + v);
            lastResult = v;
            justEvaluated = true;
        } else {
            if (key.equals("x\u00b2")) insert("^2");
            else if (key.equals("\u221ax")) insert("^(1/2)");
            else insert("1/");
        }
    }

    /** 科学计数法转换:2E5 → 2*10^5、3.5e-8 → 3.5*10^(-8)(仅当 e 前是数字且后跟数字,不影响自然常数 e) */
    private String convertSci(String expr) {
        return expr.replaceAll("(\\d(?:\\.\\d+)?)[eE]([+-]?\\d+)", "$1*10^($2)");
    }

    private void evaluate() {
        String expr = inputField.getText().trim();
        if (expr.isEmpty()) return;
        history.add(expr);
        historyIndex = -1;
        if (expr.startsWith("|")) {
            runCommand(expr.substring(1));
            inputField.setText("");
            return;
        }
        final String line = convertSci(expr);
        new Thread(() -> {
            try { calculator.evaluateAndPrint(line); }
            catch (Throwable t) {
                originalOut.println("Error: " + (t.getCause() != null ? t.getCause().getMessage() : t.getMessage()));
            }
        }).start();
    }

    private void showResult(String result) {
        if (result.startsWith("Error")) {
            justEvaluated = false;
        } else {
            lastResult = result;
            justEvaluated = true;
        }
    }

    /** 执行引擎命令(|命令,不带竖线) */
    private void runCommand(String cmd) {
        new Thread(() -> {
            try {
                Method m = calculator.class.getDeclaredMethod("handleCommand", String.class);
                m.setAccessible(true);
                m.invoke(null, cmd);
            } catch (Throwable t) {
                originalOut.println("Error: " + (t.getCause() != null ? t.getCause().getMessage() : t.getMessage()));
            }
        }).start();
    }

    private String taskName() {
        try {
            Field f = calculator.class.getDeclaredField("currentTask");
            f.setAccessible(true);
            return String.valueOf(f.get(null));
        } catch (Throwable t) { return "default"; }
    }

    private void navHistory(int dir) {
        if (history.isEmpty()) return;
        historyIndex += dir;
        if (historyIndex < 0) historyIndex = 0;
        if (historyIndex >= history.size()) { historyIndex = -1; inputField.setText(""); return; }
        inputField.setText(history.get(historyIndex));
    }

    private static String fmt(double v) {
        if (v == Math.floor(v) && Math.abs(v) < 1e15) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    // ---------- 对话框 ----------

    private JTextField darkField(String val) {
        JTextField f = new JTextField(val);
        f.setBackground(new Color(0x1A, 0x1A, 0x28));
        f.setForeground(FG_DISP);
        f.setCaretColor(FG_OP);
        f.setBorder(BorderFactory.createLineBorder(BORDER));
        return f;
    }

    private void sumOrProd(boolean isSum) {
        String name = isSum ? "\u6c42\u548c" : "\u4e58\u79ef";
        String sym = isSum ? "\u2211" : "\u220f";
        JTextField lower = darkField("1");
        JTextField upper = darkField("10");
        JTextField var = darkField("i");
        JTextField body = darkField("i^2");
        Object[] msg = {"\u4e0b\u9650 a:", lower, "\u4e0a\u9650 b:", upper,
                "\u53d8\u91cf:", var, "\u8868\u8fbe\u5f0f:", body};
        int r = JOptionPane.showConfirmDialog(frame, msg, name + "\u53c2\u6570", JOptionPane.OK_CANCEL_OPTION);
        if (r == JOptionPane.OK_OPTION) {
            inputField.setText(sym + "{" + lower.getText().trim() + "," + upper.getText().trim() + ","
                    + var.getText().trim() + "," + body.getText().trim() + "}");
            evaluate();
        }
    }

    private void limitDialog() {
        JComboBox<String> dir = new JComboBox<>(new String[]{"\u53cc\u4fa7", "\u53f3\u6781\u9650", "\u5de6\u6781\u9650"});
        JTextField target = darkField("0");
        JTextField var = darkField("x");
        JTextField body = darkField("sin(x)/x");
        Object[] msg = {"\u65b9\u5411:", dir, "\u76ee\u6807 a:", target,
                "\u53d8\u91cf:", var, "\u8868\u8fbe\u5f0f:", body};
        int r = JOptionPane.showConfirmDialog(frame, msg, "\u6781\u9650\u53c2\u6570", JOptionPane.OK_CANCEL_OPTION);
        if (r == JOptionPane.OK_OPTION) {
            String d = switch (dir.getSelectedIndex()) {
                case 1 -> "+";
                case 2 -> "-";
                default -> " ";
            };
            inputField.setText("lim{" + d + "," + target.getText().trim() + "," + var.getText().trim() + "," + body.getText().trim() + "}");
            evaluate();
        }
    }

    private void showHelp() {
        JTextArea ta = new JTextArea(
            "\u8ba1\u7b97\u5668\u4f7f\u7528\u8bf4\u660e\n"
            + "--------------------------------\n"
            + "\u57fa\u672c: \u6570\u5b57\u3001\u8fd0\u7b97\u7b26(+ - \u00d7 \u00f7 ^ %)\u3001\u62ec\u53f7\n"
            + "CE \u6e05\u9664\u3001+/- \u6b63\u8d1f\u3001x\u00b2/\u221a/1/x \u5373\u65f6\u8ba1\u7b97\n"
            + "\u5e38\u91cf: e\u3001\u03c0\u3001\u9636\u4e58(5! = 120)\n"
            + "\u51fd\u6570: \u6b63\u5f26/\u4f59\u5f26/\u6b63\u5207\u3001\u53cd\u5f26\u53cd\u4f59\u3001\u5bf9\u6570\n"
            + "\u6c42\u548c \u2211{1,10,i,i^2} = 385\u3000\u4e58\u79ef \u220f{1,5,i,i} = 120\n"
            + "\u6781\u9650 lim{+,0,x,sin(x)/x} = 1\u3000\u53cc\u4fa7: lim{,0,x,1/x}\n"
            + "\u79d1\u5b66\u8ba1\u6570\u6cd5: 2E5 = 200000\u30013.5e-8 = 3.5\u00d710^(-8)\n"
            + "\u6570\u5b57\u952e\u8f93\u5165\u53ef\u7528\u5b57\u6bcd\u76f4\u63a5\u6253\u3002| \u5f00\u5934\u4e3a\u547d\u4ee4:\n"
            + "|defineV x:Z-{0} \u3001|newTask \u3001|listTasks \u3001|version\n");
        ta.setEditable(false);
        ta.setBackground(new Color(0x0A, 0x0A, 0x14));
        ta.setForeground(FG_DISP);
        ta.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        JOptionPane.showMessageDialog(frame, new JScrollPane(ta), "\u5e2e\u52a9", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showAbout() {
        JOptionPane.showMessageDialog(frame,
                "Calculator \u9ad8\u7b49\u6570\u5b66\u8ba1\u7b97\u5668\nver 1.6.0 \u00b7 GUI\n"
                + "\u57fa\u4e8e calculator \u5f15\u64ce + Swing \u754c\u9762\nMIT License \u00a9 2026 ibuditeady",
                "\u5173\u4e8e", JOptionPane.INFORMATION_MESSAGE);
    }

    private static PrintStream originalOut;

    public static void main(String[] args) {
        originalOut = System.out;
        if (args.length >= 2 && args[0].equals("--test")) {
            for (int i = 1; i < args.length; i++) {
                try { calculator.evaluateAndPrint(args[i]); }
                catch (Throwable t) {
                    if (t.getCause() != null) System.out.println("Error: " + t.getCause().getMessage());
                    else System.out.println("Error: " + t.getMessage());
                }
            }
            System.exit(0);
        }
        SwingUtilities.invokeLater(CalculatorGUI::new);
    }
}
