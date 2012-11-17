package beast.app.beauti;


import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.xml.sax.InputSource;

//import beast.app.beauti.BeautiConnector.ConnectCondition;
import beast.app.draw.PluginPanel;
import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.Logger;
import beast.core.Plugin;
import beast.evolution.alignment.Alignment;
import beast.evolution.alignment.FilteredAlignment;
import beast.evolution.likelihood.GenericTreeLikelihood;
import beast.evolution.sitemodel.SiteModel;
import beast.evolution.substitutionmodel.SubstitutionModel;
import beast.util.XMLParser;

@Description("Template that specifies which sub-net needs to be created when " +
        "a plugin of a paricular class is created.")
public class BeautiSubTemplate extends Plugin {
    public Input<String> sClassInput = new Input<String>("class", "name of the class (with full class path) to be created", Validate.REQUIRED);
    public Input<String> sMainInput = new Input<String>("mainid", "specifies id of the main plugin to be created by the template", Validate.REQUIRED);
    //public Input<XML> sXMLInput = new Input<XML>("value","collection of objects to be created in Beast2 xml format", Validate.REQUIRED);
    public Input<String> sXMLInput = new Input<String>("value", "collection of objects to be created in Beast2 xml format", Validate.REQUIRED);
    public Input<List<BeautiConnector>> connectorsInput = new Input<List<BeautiConnector>>("connect", "Specifies which part of the template get connected to the main network", new ArrayList<BeautiConnector>());
    public Input<String> suppressedInputs = new Input<String>("suppressInputs", "comma separated list of inputs that should not be shown");

    Class<?> _class = null;
    Object instance;
    String sXML = null;
    List<BeautiConnector> connectors;

    BeautiDoc doc;

    //	String [] sSrcIDs;
//	String [] sTargetIDs;
//	String [] sTargetInputs;
//	ConnectCondition [] conditions;
    String sMainID = "";
    String sShortClassName;

    @Override
    public void initAndValidate() throws Exception {
        _class = Class.forName(sClassInput.get());
        sShortClassName = sClassInput.get().substring(sClassInput.get().lastIndexOf('.') + 1);
        instance = _class.newInstance();
        sXML = sXMLInput.get();//.m_sValue.get();
        sMainID = sMainInput.get();
        // sanity check: make sure the XML is parseable
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.newDocumentBuilder().parse(new InputSource(new StringReader("<beast>" + sXML + "</beast>")));
        // make sure there are no comments in the XML: this screws up any XML when saved to file
        if (sXML.contains("<!--")) {
            while (sXML.contains("<!--")) {
                int iStart = sXML.indexOf("<!--");
                // next line is guaranteed to find something, things we already checked this is valid XML
                int iEnd = sXML.indexOf("-->", iStart);
                sXML = sXML.substring(0, iStart) + sXML.substring(iEnd + 3);
            }
        }
        //m_sXMLInput.get().m_sValue.setValue("<![CDATA[" + m_sXML + "]]>", m_sXMLInput.get());
        sXMLInput.setValue("<![CDATA[" + sXML + "]]>", this);

        connectors = connectorsInput.get();
//		int nConnectors = connections.get().size();
//		sSrcIDs = new String[nConnectors];
//		sTargetIDs = new String[nConnectors];
//		sTargetInputs = new String[nConnectors];
////		conditions = new ConnectCondition[nConnectors];
//
//		for (int i = 0; i < nConnectors; i++) {
//			BeautiConnector connector = connections.get().get(i);
//			sSrcIDs[i] = connector.sSourceID.get();
//			sTargetIDs[i] = connector.sTargetID.get();
//			sTargetInputs[i] = connector.sInputName.get();
////			conditions[i] = connector.connectCondition.get(); 
//		}
    }

    public void setDoc(BeautiDoc doc) {
        this.doc = doc;
    }

    void removeSubNet(BeautiSubTemplate template, PartitionContext context) throws Exception {
        // disconnect all connection points in the template
        for (BeautiConnector connector : template.connectors) {
            doc.disconnect(connector, context);
        }
    }
    
    void removeSubNet(Object o) throws Exception {
        if (o == null) {
            // nothing to do
            return;
        }
        Plugin plugin = null;
        if (o instanceof Plugin) {
            plugin = (Plugin) o;
        }

        // find template that created this plugin
        String sID = plugin.getID();
        //String sPartition = BeautiDoc.parsePartition(sID);
        sID = sID.substring(0, sID.indexOf("."));
        BeautiSubTemplate template = null;
        for (BeautiSubTemplate template2 : doc.beautiConfig.subTemplatesInput.get()) {
            if (template2.matchesName(sID)) {
                template = template2;
                break;
            }
        }
        if (template == null) {
            throw new Exception("Cannot find template for removing " + plugin.getID());
        }
        PartitionContext context = doc.getContextFor(plugin);
        removeSubNet(template, context);
    }

    public Plugin createSubNet(PartitionContext partition, Plugin plugin, Input<?> input, boolean init) throws Exception {
        removeSubNet(input.get());
        if (sXML == null) {
            // this is the NULL_TEMPLATE
            input.setValue(null, plugin);
            return null;
        }
        Plugin o = createSubNet(partition, doc.pluginmap, init);
        input.setValue(o, plugin);
        return o;
    }

    public Plugin createSubNet(PartitionContext partition, List<Plugin> list, int iItem, boolean init) throws Exception {
        removeSubNet(list.get(iItem));
        if (sXML == null) {
            // this is the NULL_TEMPLATE
            list.set(iItem, null);
            return null;
        }
        Plugin o = createSubNet(partition, doc.pluginmap, init);
        list.set(iItem, o);
        return o;
    }

    public Plugin createSubNet(PartitionContext partition, boolean init) throws Exception {
        if (sXML == null) {
            // this is the NULL_TEMPLATE
            return null;
        }
        Plugin o = createSubNet(partition, doc.pluginmap, init);
        return o;
    }


    Plugin createSubNet(Alignment data, BeautiDoc doc, boolean init) {
        String sPartition = data.getID();
        HashMap<String, Plugin> sIDMap = doc.pluginmap;//new HashMap<String, Plugin>();
        sIDMap.put(sPartition, data);
        return createSubNet(new PartitionContext(sPartition), sIDMap, init);
    }

    private Plugin createSubNet(PartitionContext context, /*BeautiDoc doc,*/ HashMap<String, Plugin> sIDMap, boolean init) {
        // wrap in a beast element with appropriate name spaces
        String _sXML = "<beast version='2.0' \n" +
                "namespace='beast.app.beauti:beast.core:beast.evolution.branchratemodel:beast.evolution.speciation:beast.evolution.tree.coalescent:beast.core.util:beast.evolution.nuc:beast.evolution.operators:beast.evolution.sitemodel:beast.evolution.substitutionmodel:beast.evolution.likelihood:beast.evolution:beast.math.distributions'>\n" +
                sXML +
                "</beast>\n";

        // resolve alignment references
        _sXML = _sXML.replaceAll("idref=[\"']data['\"]", "idref='" + context.partition + "'");
        // ensure uniqueness of IDs
        _sXML = BeautiDoc.translatePartitionNames(_sXML, context);//_sXML.replaceAll("\\$\\(n\\)", sPartition);

        XMLParser parser = new XMLParser();
        parser.setRequiredInputProvider(doc, context);
        List<Plugin> plugins = null;
        try {
            plugins = parser.parseTemplate(_sXML, sIDMap, true);
            for (Plugin plugin : plugins) {
                doc.addPlugin(plugin);
                System.err.println("Adding " + plugin.getClass().getName() + " " + plugin);
            }

            for (BeautiConnector connector : connectors) {
                if (init && connector.atInitialisationOnly()) {// ||
                    doc.connect(connector, context);
                }
                if (connector.getTipText() != null) {
                    doc.tipTextMap.put(BeautiDoc.translatePartitionNames(connector.sSourceID, context), //.replaceAll("\\$\\(n\\)", sPartition),
                    		BeautiDoc.translatePartitionNames(connector.getTipText(), context)); //.replaceAll("\\$\\(n\\)", sPartition));
                }
            }
            if (suppressedInputs.get() != null) {
                String[] inputs = suppressedInputs.get().split(",");
                for (String input : inputs) {
                    input = input.trim();
                    doc.beautiConfig.suppressPlugins.add(input);
                }
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        if (sMainID.equals("[top]")) {
            return plugins.get(0);
        }

        String sID = sMainID;
        sID = BeautiDoc.translatePartitionNames(sID, context); //sID.replaceAll("\\$\\(n\\)", sPartition);
        Plugin plugin = doc.pluginmap.get(sID);

        if (this == doc.beautiConfig.partitionTemplate.get()) {
            // HACK: need to make sure the subst model is of the correct type
            Plugin treeLikelihood = doc.pluginmap.get("treeLikelihood." + context.partition);
            if (treeLikelihood != null && ((GenericTreeLikelihood) treeLikelihood).m_pSiteModel.get() instanceof SiteModel.Base) {
	            SiteModel.Base siteModel = (SiteModel.Base) ((GenericTreeLikelihood) treeLikelihood).m_pSiteModel.get();
	            SubstitutionModel substModel = siteModel.m_pSubstModel.get();
	            try {
	                siteModel.canSetSubstModel(substModel);
	            } catch (Exception e) {
	                Object o = doc.createInput(siteModel, siteModel.m_pSubstModel, context);
	                try {
	                    siteModel.m_pSubstModel.setValue(o, siteModel);
	                } catch (Exception ex) {
	                    ex.printStackTrace();
	                }
	            }
            }

            // HACK2: rename file name for trace log if it has the default value
            Logger logger = (Logger) doc.pluginmap.get("tracelog");
            if (logger != null) {
	            String fileName = logger.m_pFileName.get();
	            if (fileName.startsWith("beast.") && treeLikelihood != null) {
	            	Alignment data = ((GenericTreeLikelihood)treeLikelihood).m_data.get();
	            	while (data instanceof FilteredAlignment) {
	            		data = ((FilteredAlignment) data).m_alignmentInput.get();
	            	}
	            	fileName = data.getID() + fileName.substring(5);
	            	try {
						logger.m_pFileName.setValue(fileName, logger);
					} catch (Exception e) {
						e.printStackTrace();
					}
	            }
            }
        }

        //System.err.println(new XMLProducer().toXML(plugin));
        return plugin;
    }

    public String getMainID() {
        return sMainID;
    }


    @Override
    public String toString() {
        String sID = getID();
        sID = sID.replaceAll("([a-z])([A-Z])", "$1 $2");
        return sID;
    }


    public boolean matchesName(String sID) {
        if (getMainID().replaceAll(".\\$\\(n\\)", "").equals(sID)) {
            return true;
        }
        if (getMainID().replaceAll("..:\\$\\(n\\)", "").equals(sID)) {
            return true;
        }
        if (sShortClassName != null && sShortClassName.equals(sID)) {
            return true;
        }
        return false;
    }
}
