/*
 * jets3t : Java Extra-Tasty S3 Toolkit (for Amazon S3 online storage service)
 * This is a java.net project, see https://jets3t.dev.java.net/
 * 
 * Copyright 2006 James Murty
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.jets3t.apps.uploader;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jets3t.gui.HyperlinkActivatedListener;
import org.jets3t.gui.JHtmlLabel;
import org.jets3t.gui.skins.SkinsFactory;
import org.jets3t.service.Jets3tProperties;

/**
 * Manages the display of property-configured input fields in a panel, and collection of
 * the user-entered results as properties.
 * 
 * @author James Murty
 */
public class UserInputFields {
    private static final Log log = LogFactory.getLog(UserInputFields.class);
    
    private GridBagLayout GRID_BAG_LAYOUT = null;
    private Insets insetsDefault = null;
    private HyperlinkActivatedListener hyperlinkListener = null;
    private SkinsFactory skinsFactory = null;
    
    private Insets insetsNone = new Insets(0, 0, 0, 0);
    private Map userInputComponentsMap = new HashMap();
    
    /**
     * Constructs the object ready to generate GUI elements to represent the configured
     * user input fields.
     * 
     * @param defaultGridBagLayout
     * the default grid bag layout to use when displaying the GUI elements.
     * @param defaultInsets
     * the default insets to use when displaying the GUI elements.
     * @param hyperlinkListener
     * a class to listen for hyperlink click events that may be generated by {@link JHtmlLabel}.
     * This class may be null, in which case these events will be ignored.
     * @param skinsFactory
     * the skin factory used to create GUI elements.
     */
    public UserInputFields(GridBagLayout defaultGridBagLayout, Insets defaultInsets, 
        HyperlinkActivatedListener hyperlinkListener, SkinsFactory skinsFactory) 
    {
        GRID_BAG_LAYOUT = defaultGridBagLayout;
        this.insetsDefault = defaultInsets;
        this.hyperlinkListener = hyperlinkListener;
        this.skinsFactory = skinsFactory;
    }

    /**
     * Builds a user input panel matching the fields specified in the uploader.properties file.
     * 
     * @param fieldsPanel
     * the panel component to add prompt and user input components to.
     * @param uploaderProperties
     * properties specific to the Uploader application that includes the field.* settings 
     * necessary to build the User Inputs screen.
     */
    public void buildFieldsPanel(JPanel fieldsPanel, Jets3tProperties uploaderProperties) {
        int fieldRow = 0;
        
        for (int fieldNo = 0; fieldNo < 100; fieldNo++) {
            String fieldName = uploaderProperties.getStringProperty("field." + fieldNo + ".name", null);
            String fieldType = uploaderProperties.getStringProperty("field." + fieldNo + ".type", null);
            String fieldPrompt = uploaderProperties.getStringProperty("field." + fieldNo + ".prompt", null);
            String fieldOptions = uploaderProperties.getStringProperty("field." + fieldNo + ".options", null);
            
            if (fieldName == null) {
                // Expect there to be at least one field at number 0 or 1. If not, stop looking.
                if (fieldNo > 1) {
                    return;
                } else {
                    continue;
                }
            } else {
                if (fieldType == null || fieldPrompt == null) {
                    log.warn("Field '" + fieldName + "' missing .type or .prompt properties");
                    continue;
                }
                if ("radio".equals(fieldType)) {
                    if (fieldOptions == null) {
                        log.warn("Radio button field '" + fieldName + "' is missing the required .options property");
                        continue;
                    }
                    
                    JHtmlLabel label = skinsFactory.createSkinnedJHtmlLabel(fieldName);
                    label.setText(fieldPrompt);
                    label.setHyperlinkeActivatedListener(hyperlinkListener);
                    fieldsPanel.add(label,
                        new GridBagConstraints(0, fieldRow++, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, insetsDefault, 0, 0));

                    JPanel optionsPanel = skinsFactory.createSkinnedJPanel("OptionsPanel");
                    optionsPanel.setLayout(GRID_BAG_LAYOUT);
                    int columnOffset = 0;
                    ButtonGroup buttonGroup = new ButtonGroup();                    
                    StringTokenizer st = new StringTokenizer(fieldOptions, ",");
                    while (st.hasMoreTokens()) {
                        String option = st.nextToken();
                        JRadioButton radioButton = new JRadioButton(option);
                        buttonGroup.add(radioButton);
                        if (buttonGroup.getButtonCount() == 1) {
                            // Make first button the default.
                            radioButton.setSelected(true);
                        }

                        optionsPanel.add(radioButton,
                            new GridBagConstraints(columnOffset++, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, insetsDefault, 0, 0));
                    }
                    fieldsPanel.add(optionsPanel,
                        new GridBagConstraints(0, fieldRow++, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, insetsNone, 0, 0));

                    userInputComponentsMap.put(fieldName, buttonGroup);
                } else if ("selection".equals(fieldType)) {
                    if (fieldOptions == null) {
                        log.warn("Radio button field '" + fieldName + "' is missing the required .options property");
                        continue;
                    }
                    
                    JHtmlLabel label = skinsFactory.createSkinnedJHtmlLabel(fieldName);
                    label.setText(fieldPrompt);
                    label.setHyperlinkeActivatedListener(hyperlinkListener);
                    fieldsPanel.add(label,
                        new GridBagConstraints(0, fieldRow++, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, insetsDefault, 0, 0));

                    JComboBox comboBox = new JComboBox();
                    StringTokenizer st = new StringTokenizer(fieldOptions, ",");
                    while (st.hasMoreTokens()) {
                        String option = st.nextToken();
                        comboBox.addItem(option);
                    }
                    fieldsPanel.add(comboBox,
                        new GridBagConstraints(0, fieldRow++, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, insetsDefault, 0, 0));
        
                    userInputComponentsMap.put(fieldName, comboBox);
                } else if ("text".equals(fieldType)) {
                    JHtmlLabel label = skinsFactory.createSkinnedJHtmlLabel(fieldName);
                    label.setText(fieldPrompt);
                    label.setHyperlinkeActivatedListener(hyperlinkListener);
                    fieldsPanel.add(label,
                        new GridBagConstraints(0, fieldRow++, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, insetsDefault, 0, 0));
                    JTextField textField = new JTextField();
                    fieldsPanel.add(textField,
                        new GridBagConstraints(0, fieldRow++, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, insetsDefault, 0, 0));
                    
                    userInputComponentsMap.put(fieldName, textField);
                } else if ("password".equals(fieldType)) {
                    JHtmlLabel label = skinsFactory.createSkinnedJHtmlLabel(fieldName);
                    label.setText(fieldPrompt);
                    label.setHyperlinkeActivatedListener(hyperlinkListener);
                    fieldsPanel.add(label,
                        new GridBagConstraints(0, fieldRow++, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, insetsDefault, 0, 0));
                    JPasswordField passwordField = new JPasswordField();
                    fieldsPanel.add(passwordField,
                        new GridBagConstraints(0, fieldRow++, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, insetsDefault, 0, 0));
                    
                    userInputComponentsMap.put(fieldName, passwordField);
                } else if (fieldType.startsWith("textarea")) {
                    JHtmlLabel label = skinsFactory.createSkinnedJHtmlLabel(fieldName);
                    label.setText(fieldPrompt);
                    label.setHyperlinkeActivatedListener(hyperlinkListener);
                    fieldsPanel.add(label,
                        new GridBagConstraints(0, fieldRow++, 2, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, insetsDefault, 0, 0));                    
                    JTextArea textArea = new JTextArea();
                    fieldsPanel.add(new JScrollPane(textArea),
                        new GridBagConstraints(0, fieldRow++, 2, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.BOTH, insetsDefault, 0, 0));
                    
                    userInputComponentsMap.put(fieldName, textArea);
                } else {
                    log.warn("Unrecognised .type setting for field '" + fieldName + "'");
                }                
            }
        }
    }
    
    /**
     * @return
     * properties containing the user's answers to the input fields. Property names correspond to
     * the field's name, and the property values are the user's response.
     */
    public Properties getUserInputsAsProperties() {
        Properties properties = new Properties();
        
        for (Iterator iter = userInputComponentsMap.keySet().iterator(); iter.hasNext();) {
            String fieldName = (String) iter.next();
            String fieldValue = null;
            
            Object component = userInputComponentsMap.get(fieldName);
            if (component instanceof ButtonGroup) {
                ButtonGroup bg = (ButtonGroup) component;
                Enumeration radioEnum = bg.getElements();
                while (radioEnum.hasMoreElements()) {
                    JRadioButton button = (JRadioButton) radioEnum.nextElement();
                    if (button.isSelected()) {
                        fieldValue = button.getText();
                        break;
                    }
                }
            } else if (component instanceof JComboBox) {
                fieldValue = ((JComboBox) component).getSelectedItem().toString();
            } else if (component instanceof JTextField) {
                fieldValue = ((JTextField) component).getText();
            } else if (component instanceof JPasswordField) {
                fieldValue = new String(((JPasswordField) component).getPassword());
            } else if (component instanceof JTextArea) {            
                fieldValue = ((JTextArea) component).getText();
            } else {
                log.warn("Unrecognised component type for field named '" + fieldName + "': "
                    + component.getClass().getName());
            }
            
            properties.put(fieldName, fieldValue);
        }
        return properties;
    }
    
    public boolean isUserInputFieldsAvailable() {
        return userInputComponentsMap.size() > 0;
    }
    
}
