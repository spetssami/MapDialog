

// Kartankatseluohjelman graafinen käyttöliittymä

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.io.IOException;
import javax.xml.parsers.*;
import javax.xml.xpath.*;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class MapDialog extends JFrame {

    private final String ADDRESS = "https://demo.mapserver.org/cgi-bin/wms?SERVICE=WMS&VERSION=1.1.1";
    private final String SRS = "EPSG:4326";
    
    
    // Resoluutio ja tyyppi

    private final int WIDTH = 1240;
    private final int HEIGHT = 760;
    private final String IMG = "image/png";
    private final boolean TRANSPARENT = true;

    // Koordinaatit

    private int x = 0;
    private int y = 0;
    private int zoom = 90;
    private int offset = 20; // vakio jolla muutetaan x ja y arvoja (paljonko liikkuu vasemmalle, oikealle)

    // Käyttöliittymän komponentit

    private static JLabel imageLabel = new JLabel();
    private JPanel leftPanel = new JPanel();

    private List<LayerCheckBox> checkedBoxes = new ArrayList<>();
    ExecutorService executor = Executors.newFixedThreadPool(1);

    private JButton refreshB = new JButton("PÄIVITÄ");
    private JButton leftB = new JButton("<");
    private JButton rightB = new JButton(">");
    private JButton upB = new JButton("^");
    private JButton downB = new JButton("v");
    private JButton zoomInB = new JButton("+");
    private JButton zoomOutB = new JButton("-");
    private Boolean running;
    
    public MapDialog() throws Exception {
    	

        // Valmistelee ikkunan ja lisää siihen komponentit

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setLayout(new BorderLayout());

        
        //Käynnistää käyttöliittymän bluemarble näkymällä
        new Run_thread("bluemarble").run();

        add(imageLabel, BorderLayout.EAST);
        
        //Nappuloiden luontia
        ButtonListener bl = new ButtonListener();
        refreshB.addActionListener(bl);
        leftB.addActionListener(bl);
        rightB.addActionListener(bl);
        upB.addActionListener(bl);
        downB.addActionListener(bl);
        zoomInB.addActionListener(bl);
        zoomOutB.addActionListener(bl);
        
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        leftPanel.setMaximumSize(new Dimension(100, 600));

        addedLayers();
        
        //Nappuloiden lisäämistä käyttöliittymään
        leftPanel.add(refreshB);
        leftPanel.add(Box.createVerticalStrut(20));
        leftPanel.add(leftB);
        leftPanel.add(rightB);
        leftPanel.add(upB);
        leftPanel.add(downB);
        leftPanel.add(zoomInB);
        leftPanel.add(zoomOutB);

        add(leftPanel, BorderLayout.WEST);
        
        running = true;
        pack();
        setVisible(true);
    }

    public static void main(String[] args) throws Exception {
    	MapDialog app = new MapDialog();
    	while(app.running) {
    		System.out.println(Thread.activeCount());
        }
    }
    
    // Kontrollinappien kuuntelija
    // Napeilla voi liikkua näkymässä ylös, alas, vasemmalle ja oikealle.
    // Napeilla voi myös zoomata sisään ja ulos.
    
    private class ButtonListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            if (e.getSource() == refreshB) {
            	updateImage();
            }
            if (e.getSource() == leftB) {
                // VASEMMALLE SIIRTYMINEN KARTALLA
                x = x - offset;     
                updateImage();
            }
            if (e.getSource() == rightB) {
                // OIKEALLE SIIRTYMINEN KARTALLA
                x = x + offset;
                updateImage();
            }
            if (e.getSource() == upB) {
                // YLÖSPÄIN SIIRTYMINEN KARTALLA
                y = y + offset;
                updateImage();
            }
            if (e.getSource() == downB) {
                // ALASPÄIN SIIRTYMINEN KARTALLA
                y = y - offset;
                updateImage();
            }
            if (e.getSource() == zoomInB) {
                // ZOOM IN -TOIMINTO
                zoom = new Double(zoom*0.75).intValue();
                updateImage();
            } 
            if (e.getSource() == zoomOutB) {
                // ZOOM OUT -TOIMINTO
                zoom = new Double(zoom*1.25).intValue();
                updateImage();
            }
        }  
    }
    
    
    // Lisää valittujen checkboxien layerit käyttöliittymän näkymään
    private void addedLayers() {
        String url = ADDRESS + "&REQUEST=GetCapabilities";
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(new URL(url).openStream());

            for (String l : getLayers(doc)) {
                LayerCheckBox b = new LayerCheckBox(l, l, false);
                checkedBoxes.add(b);
                leftPanel.add(b);
            }
        } catch (ParserConfigurationException | IOException | SAXException e) {
            e.printStackTrace();
        }
    }
    
    //Palauttaa listan xml-tiedoston layertageista
    private static List<String> getLayers(Document doc) {
        List<String> list = new ArrayList<>();

        XPathFactory factory = XPathFactory.newInstance();
        XPath xpath = factory.newXPath();
        String str = "/WMT_MS_Capabilities/Capability/Layer/Layer/Name/text()";
        try { 
            XPathExpression exprs = xpath.compile(str);
            NodeList nodelist = (NodeList) exprs.evaluate(doc, XPathConstants.NODESET);
            for (int i=0; i<nodelist.getLength(); i++) {
                list.add(nodelist.item(i).getNodeValue());
            }
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }
        return list;
    }

    // Lataa uuden kuvan valittujen layereitten perusteella
    public void updateImage()  {
    	
    	// Käy läpi checkboxit ja lisää valitut laatikot listana muuttujaan s,
    	// pilkulla erottaen. Muuttujalla s avulla haetaan uusi kuva palvelimelta.
    	String s = String.join(",", checkedBoxes.stream()
                .filter(checkBox -> checkBox.isSelected())
                .map(checkBox -> checkBox.getName())
                .collect(Collectors.toList()));
    	
    	//Suorittaa kuvanhaun palvelimelta omassa säikeessä
    	Runnable getData = new Runnable(){
    		public void run() {
	    		String layers = s;
	    		int param1 = x - 2 * zoom;
	            int param2 = y - zoom;
	            int param3 = x + 2 * zoom;
	            int param4 = y + zoom;
	            
	            String url = ADDRESS
	                    + "&REQUEST=GetMap"
	                    + String.format("&BBOX=" + param1 + "," + param2 + "," + param3 + "," + param4)
	                    + "&SRS=" + SRS
	                    + "&WIDTH=" + WIDTH
	                    + "&HEIGHT=" + HEIGHT
	                    + "&LAYERS=" + layers
	                    + "&STYLES="
	                    + "&FORMAT=" + IMG
	                    + "&TRANSPARENT=" + TRANSPARENT;
	
	            //Yrittää hakea kuvan palvelimelta
	            //ja suorittaa kuvan lataamisen EDT-säikeessä
	            try {
	            	final ImageIcon img = new ImageIcon(new URL(url));
	                SwingUtilities.invokeLater(() -> imageLabel.setIcon(img));
	            }catch(MalformedURLException e) {
	            	System.out.println(e);
	            }
    		}
    	};
    	//Pitää huolen että kuvia hakevia säikeitä on käynnissä korkeintaa yksi kerrallaan
    	executor.execute(getData);
    }

    // Valintalaatikko, joka muistaa karttakerroksen nimen
    private class LayerCheckBox extends JCheckBox {
        private String name = "";

        public LayerCheckBox(String name, String title, boolean selected) {
            super(title, null, selected);
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    // Hakee uuden kuvan valituilla parametreilla.
    private class Run_thread extends Thread {
        private String layers;

        public Run_thread(String s) {
            this.layers = s;
        }

        // run
        public void run() {
            int param1 = x - 2 * zoom;
            int param2 = y - zoom;
            int param3 = x + 2 * zoom;
            int param4 = y + zoom;
            
            System.out.println(layers);

            String url = ADDRESS
                    + "&REQUEST=GetMap"
                    + String.format("&BBOX=" + param1 + "," + param2 + "," + param3 + "," + param4)
                    + "&SRS=" + SRS
                    + "&WIDTH=" + WIDTH
                    + "&HEIGHT=" + HEIGHT
                    + "&LAYERS=" + layers
                    + "&STYLES="
                    + "&FORMAT=" + IMG
                    + "&TRANSPARENT=" + TRANSPARENT;
            try {
                imageLabel.setIcon(new ImageIcon(new URL(url)));
            } catch (MalformedURLException m) {
                m.printStackTrace();
            }
        }
    }

} // MapDialog