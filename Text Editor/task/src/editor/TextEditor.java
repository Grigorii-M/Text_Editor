package editor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextEditor extends JFrame {
    private final JFileChooser jfc = new JFileChooser();
    private final JTextArea textArea = new JTextArea();
    private ArrayList<SearchResult> searchResults;
    private int currentMatchIndex;

    public TextEditor() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        setSize(660, 400);
        setTitle("Text editor");

        jfc.setName("FileChooser");
        add(jfc);

        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        fileMenu.setName("MenuFile");
        fileMenu.setMnemonic(KeyEvent.VK_F);

        JMenuItem loadMenuItem = new JMenuItem("Open");
        loadMenuItem.setName("MenuOpen");
        JMenuItem saveMenuItem = new JMenuItem("Save");
        saveMenuItem.setName("MenuSave");
        JMenuItem exitMenuItem = new JMenuItem("Exit");
        exitMenuItem.setName("MenuExit");
        exitMenuItem.addActionListener(actionEvent -> System.exit(0));

        fileMenu.add(loadMenuItem);
        fileMenu.add(saveMenuItem);
        fileMenu.addSeparator();
        fileMenu.add(exitMenuItem);

        JMenu searchMenu = new JMenu("Search");
        searchMenu.setName("MenuSearch");
        searchMenu.setMnemonic(KeyEvent.VK_S);

        JMenuItem startSearchMenuItem = new JMenuItem("Start search");
        startSearchMenuItem.setName("MenuStartSearch");
        JMenuItem previousMatchMenuItem = new JMenuItem("Previous match");
        previousMatchMenuItem.setName("MenuPreviousMatch");
        JMenuItem nextMatchMenuItem = new JMenuItem("Next match");
        nextMatchMenuItem.setName("MenuNextMatch");
        JCheckBoxMenuItem useRegExMenuItem = new JCheckBoxMenuItem("Use RegEx");
        useRegExMenuItem.setName("MenuUseRegExp");

        searchMenu.add(startSearchMenuItem);
        searchMenu.add(previousMatchMenuItem);
        searchMenu.add(nextMatchMenuItem);
        searchMenu.add(useRegExMenuItem);

        menuBar.add(fileMenu);
        menuBar.add(searchMenu);
        setJMenuBar(menuBar);

        textArea.setName("TextArea");

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setName("ScrollPane");
        add(scrollPane, BorderLayout.CENTER);

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEADING, 5, 5));

        JButton saveButton = new JButton(UIManager.getIcon("FileView.floppyDriveIcon"));
        saveButton.setName("SaveButton");
        saveButton.addActionListener(actionEvent -> save(textArea));
        saveMenuItem.addActionListener(actionEvent -> save(textArea));

        JButton openButton = new JButton(UIManager.getIcon("FileView.directoryIcon"));
        openButton.setName("OpenButton");
        openButton.addActionListener(actionEvent -> open(textArea));
        loadMenuItem.addActionListener(actionEvent -> open(textArea));

        JTextField searchField = new JTextField(20);
        searchField.setName("SearchField");

        JCheckBox useRegExCheckbox = new JCheckBox("Use RegEx");
        useRegExCheckbox.addActionListener(actionEvent -> useRegExMenuItem.setState(useRegExCheckbox.isSelected()));
        useRegExMenuItem.addActionListener(actionEvent -> useRegExCheckbox.setSelected(useRegExMenuItem.isSelected()));
        useRegExCheckbox.setName("UseRegExCheckbox");

        JButton searchButton = new JButton("Search");
        searchButton.setName("StartSearchButton");
        searchButton.addActionListener(actionEvent -> startSearch(searchField, useRegExCheckbox));
        startSearchMenuItem.addActionListener(actionEvent -> startSearch(searchField, useRegExCheckbox));

        JButton previousMatchButton = new JButton("Prev");
        previousMatchButton.setName("PreviousMatchButton");
        previousMatchButton.addActionListener(actionEvent -> previousMatch());
        previousMatchMenuItem.addActionListener(actionEvent -> previousMatch());

        JButton nextMatchButton = new JButton("Next");
        nextMatchButton.setName("NextMatchButton");
        nextMatchButton.addActionListener(actionEvent -> nextMatch());
        nextMatchMenuItem.addActionListener(actionEvent -> nextMatch());

        panel.add(saveButton);
        panel.add(openButton);
        panel.add(searchField);
        panel.add(searchButton);
        panel.add(previousMatchButton);
        panel.add(nextMatchButton);
        panel.add(useRegExCheckbox);

        add(panel, BorderLayout.NORTH);

        setVisible(true);
    }

    private void save(JTextArea textComponent) {
        int result = jfc.showSaveDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = jfc.getSelectedFile();
            String data = textComponent.getText();
            try (FileWriter writer = new FileWriter(selectedFile)) {
                writer.write(data);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void open(JTextArea textComponent) {
        int result = jfc.showOpenDialog(null);
        textComponent.setText("");
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = jfc.getSelectedFile();
            try (InputStream reader = new FileInputStream(selectedFile)) {
                byte[] data = reader.readAllBytes();
                String str = new String(data);
                textComponent.setText(str);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void startSearch(JTextField textField, JCheckBox useRegEx) {
        String searchFor = textField.getText();
        String searchIn = textArea.getText();
        TextSearchWorker searcher = new TextSearchWorker(searchFor, searchIn, useRegEx.isSelected());
        searcher.execute();
    }

    private void previousMatch() {
        if (!searchResults.isEmpty()) {
            if (currentMatchIndex == 0) {
                currentMatchIndex = searchResults.size() - 1;
            } else {
                currentMatchIndex--;
            }

            int index = searchResults.get(currentMatchIndex).index;
            String foundText = searchResults.get(currentMatchIndex).data;

            selectText(index, foundText);
        }
    }

    private void nextMatch() {
        if (!searchResults.isEmpty()) {
            if (currentMatchIndex == searchResults.size() - 1) {
                currentMatchIndex = 0;
            } else {
                currentMatchIndex++;
            }

            int index = searchResults.get(currentMatchIndex).index;
            String foundText = searchResults.get(currentMatchIndex).data;

            selectText(index, foundText);
        }
    }

    private void selectText(int index, String data) {
        textArea.setCaretPosition(index + data.length());
        textArea.select(index, index + data.length());
        textArea.grabFocus();
    }

    public class TextSearchWorker extends SwingWorker<ArrayList<SearchResult>, Void> {

        private final String searchFor;
        private String searchIn;
        private final boolean useRegEx;
        private int numberRemoved = 0;

        public TextSearchWorker(String searchFor, String searchIn, boolean useRegEx) {
            this.searchFor = searchFor;
            this.searchIn = searchIn;
            this.useRegEx = useRegEx;
        }

        @Override
        protected ArrayList<SearchResult> doInBackground() throws Exception {
            ArrayList<SearchResult> result = new ArrayList<>();
            if (useRegEx) {
                Matcher matcher = Pattern.compile(searchFor).matcher(searchIn);

                while (matcher.find()) {
                    String data = matcher.group();
                    result.add(new SearchResult(numberRemoved + searchIn.indexOf(data), data));
                    numberRemoved += searchIn.indexOf(data) + data.length();
                    searchIn = searchIn.substring(searchIn.indexOf(data) + data.length());
                    matcher = Pattern.compile(searchFor).matcher(searchIn);
                }
            } else {
                while (searchIn.contains(searchFor)) {
                    int index = searchIn.indexOf(searchFor);
                    result.add(new SearchResult(numberRemoved + index, searchFor));
                    numberRemoved += searchIn.indexOf(searchFor) + searchFor.length();
                    searchIn = searchIn.substring(searchIn.indexOf(searchFor) + searchFor.length());
                }
            }

            return result;
        }

        @Override
        protected void done() {
            try {
                searchResults = get();
                if (!searchResults.isEmpty()) {
                    currentMatchIndex = 0;

                    int index = searchResults.get(currentMatchIndex).index;
                    String foundText = searchResults.get(currentMatchIndex).data;

                    selectText(index, foundText);
                }
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
    }
}
