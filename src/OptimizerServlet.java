import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import optimizer.Pipe.FlowType;
import optimizer.Pipe;
import optimizer.PipeCost;
import optimizer.Util;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ConditionalFormattingRule;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PatternFormatting;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetConditionalFormatting;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import structs.CommercialPipeStruct;
import structs.EsrCostStruct;
import structs.EsrGeneralStruct;
import structs.GeneralStruct;
import structs.MapNodeStruct;
import structs.MapPipeStruct;
import structs.NodeStruct;
import structs.PipeStruct;
import structs.PumpGeneralStruct;
import structs.PumpManualStruct;
import structs.ResultEsrStruct;
import structs.ResultPumpStruct;
import structs.ValveStruct;

import com.google.gson.Gson;

//servlet class to handle post calls

@WebServlet("/optimizer")
public class OptimizerServlet extends HttpServlet
{		
	private static final long serialVersionUID = 1L;
    
	//version number of JalTantra
	private String version = "2.2.2.0";
	
    public OptimizerServlet() 
    {
        super();
    }

    //generate and upload XML input network file 
    protected void uploadXmlInputFile(HttpServletRequest request, HttpServletResponse response)
    {
    	try 
		{
	    	OutputStream os = response.getOutputStream();
		
			Gson gson = new Gson(); 
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
	 
			Document doc = docBuilder.newDocument();
			Element rootElement = doc.createElement("root");
			doc.appendChild(rootElement);
	 
			GeneralStruct generalProperties = gson.fromJson(request.getParameter("general"), GeneralStruct.class);
			
			if(generalProperties.name_project==null || generalProperties.name_project.isEmpty())
				generalProperties.name_project="JalTantra Project";
			
			Element nameElement = doc.createElement("name");
			nameElement.appendChild(doc.createTextNode(generalProperties.name_project));
			rootElement.appendChild(nameElement);
			
			Element organizationElement = doc.createElement("organization");
			organizationElement.appendChild(doc.createTextNode(generalProperties.name_organization));
			rootElement.appendChild(organizationElement);
			
			Element versionElement = doc.createElement("version");
			versionElement.appendChild(doc.createTextNode(version));
			rootElement.appendChild(versionElement);
	 			
			response.setContentType("application/xml"); // Set up mime type
			response.setHeader("Content-Disposition", "attachment; filename="+generalProperties.name_project+".xml");
						
			// node elements
			Element nodes = doc.createElement("nodes");
			rootElement.appendChild(nodes);
			
			Element defaultMinPressure = doc.createElement("defaultMinPressure");
			defaultMinPressure.appendChild(doc.createTextNode(Double.toString(generalProperties.min_node_pressure)));
			nodes.appendChild(defaultMinPressure);
			
			//Element peakFactor = doc.createElement("peakFactor");
			//peakFactor.appendChild(doc.createTextNode(textFieldPeakFactor.getText()));
			//nodes.appendChild(peakFactor);
			
			Element supplyHours = doc.createElement("supplyHours");
			supplyHours.appendChild(doc.createTextNode(Double.toString(generalProperties.supply_hours)));
			nodes.appendChild(supplyHours);
			
			Element sourceNode = doc.createElement("source");			
			sourceNode.setAttribute("nodeID", Integer.toString(generalProperties.source_nodeid));
			sourceNode.setAttribute("nodeName", generalProperties.source_nodename);
			sourceNode.setAttribute("elevation", Double.toString(generalProperties.source_elevation));
			sourceNode.setAttribute("head", Double.toString(generalProperties.source_head));					
			nodes.appendChild(sourceNode);
											
			NodeStruct[] nodesArray = gson.fromJson(request.getParameter("nodes"), NodeStruct[].class);
			if(nodesArray!=null && nodesArray.length!=0)
			{
				for(NodeStruct node : nodesArray)
				{		
					Element nodeElement = doc.createElement("node");
					
					nodeElement.setAttribute("nodeID", Integer.toString(node.nodeid));
					nodeElement.setAttribute("nodeName", node.nodename);					
					nodeElement.setAttribute("elevation", Double.toString(node.elevation));
					if(node.demand!=0)
						nodeElement.setAttribute("demand", Double.toString(node.demand));
					if(node.minpressure!=0)
						nodeElement.setAttribute("minPressure", Double.toString(node.minpressure));
					nodes.appendChild(nodeElement);
				}
			}
							
			Element pipes = doc.createElement("pipes");
			rootElement.appendChild(pipes);
			
			Element defaultRoughness = doc.createElement("defaultRoughness");
			defaultRoughness.appendChild(doc.createTextNode(Double.toString(generalProperties.def_pipe_roughness)));
			pipes.appendChild(defaultRoughness);
			
			Element minHeadLossPerKM = doc.createElement("minHeadLossPerKM");
			minHeadLossPerKM.appendChild(doc.createTextNode(Double.toString(generalProperties.min_hl_perkm)));
			pipes.appendChild(minHeadLossPerKM);
			
			Element maxHeadLossPerKM = doc.createElement("maxHeadLossPerKM");
			maxHeadLossPerKM.appendChild(doc.createTextNode(Double.toString(generalProperties.max_hl_perkm)));
			pipes.appendChild(maxHeadLossPerKM);
			
			if(generalProperties.max_water_speed > 0){
				Element maxWaterSpeed = doc.createElement("maxWaterSpeed");
				maxWaterSpeed.appendChild(doc.createTextNode(Double.toString(generalProperties.max_water_speed)));
				pipes.appendChild(maxWaterSpeed);
			}
			
			if(generalProperties.max_pipe_pressure > 0){
				Element maxPipePressure = doc.createElement("maxPipePressure");
				maxPipePressure.appendChild(doc.createTextNode(Double.toString(generalProperties.max_pipe_pressure)));
				pipes.appendChild(maxPipePressure);
			}
			
			PipeStruct[] pipesArray = gson.fromJson(request.getParameter("pipes"), PipeStruct[].class);
			if(pipesArray!=null && pipesArray.length!=0)
			{
				for(PipeStruct pipe : pipesArray)
				{
					Element pipeElement = doc.createElement("pipe");
					
					pipeElement.setAttribute("pipeID", Integer.toString(pipe.pipeid));
					pipeElement.setAttribute("length", Double.toString(pipe.length));
					pipeElement.setAttribute("startNode", Integer.toString(pipe.startnode));
					pipeElement.setAttribute("endNode", Integer.toString(pipe.endnode));
					if(pipe.diameter!=0)
						pipeElement.setAttribute("diameter", Double.toString(pipe.diameter));
					if(pipe.roughness!=0)
						pipeElement.setAttribute("roughness", Double.toString(pipe.roughness));
					if(pipe.parallelallowed)
						pipeElement.setAttribute("parallelAllowed", Boolean.toString(pipe.parallelallowed));

					pipes.appendChild(pipeElement);
				}
			}
			
			Element pipeCosts = doc.createElement("pipeCosts");
			rootElement.appendChild(pipeCosts);
	 
			CommercialPipeStruct[] commercialPipesArray = gson.fromJson(request.getParameter("commercialpipes"), CommercialPipeStruct[].class);
			if(commercialPipesArray!=null && commercialPipesArray.length!=0)
			{
				for(CommercialPipeStruct commercialPipe : commercialPipesArray)
				{				
					Element pipeCostElement = doc.createElement("pipeCost");
					pipeCostElement.setAttribute("diameter", Double.toString(commercialPipe.diameter));
					if(commercialPipe.roughness!=0)
						pipeCostElement.setAttribute("roughness", Double.toString(commercialPipe.roughness));
					pipeCostElement.setAttribute("cost", Double.toString(commercialPipe.cost));
					
					pipeCosts.appendChild(pipeCostElement);
				}
			}	
			
			EsrGeneralStruct esrGeneralProperties = gson.fromJson(request.getParameter("esrGeneral"), EsrGeneralStruct.class);
			EsrCostStruct[] esrCostsArray = gson.fromJson(request.getParameter("esrCost"), EsrCostStruct[].class);
			
			if(esrGeneralProperties!=null){
				Element esr = doc.createElement("esr");
				rootElement.appendChild(esr);
				
				Element esrEnabled = doc.createElement("esr_enabled");
				esrEnabled.appendChild(doc.createTextNode(Boolean.toString(esrGeneralProperties.esr_enabled)));
				esr.appendChild(esrEnabled);
				
				Element secondarySupplyHours = doc.createElement("secondary_supply_hours");
				secondarySupplyHours.appendChild(doc.createTextNode(Double.toString(esrGeneralProperties.secondary_supply_hours)));
				esr.appendChild(secondarySupplyHours);
				
				Element esrCapacityFactor = doc.createElement("esr_capacity_factor");
				esrCapacityFactor.appendChild(doc.createTextNode(Double.toString(esrGeneralProperties.esr_capacity_factor)));
				esr.appendChild(esrCapacityFactor);
				
				Element maxEsrHeight = doc.createElement("max_esr_height");
				maxEsrHeight.appendChild(doc.createTextNode(Double.toString(esrGeneralProperties.max_esr_height)));
				esr.appendChild(maxEsrHeight);
				
				Element allowDummy = doc.createElement("allow_dummy");
				allowDummy.appendChild(doc.createTextNode(Boolean.toString(esrGeneralProperties.allow_dummy)));
				esr.appendChild(allowDummy);
				
				Element mustEsrs = doc.createElement("must_esrs");		
				if(esrGeneralProperties.must_esr!=null){
					for(int i: esrGeneralProperties.must_esr){
						Element mustEsr = doc.createElement("must_esr");
						mustEsr.appendChild(doc.createTextNode(Integer.toString(i)));
						mustEsrs.appendChild(mustEsr);
					}
				}
				esr.appendChild(mustEsrs);
				
				Element mustNotEsrs = doc.createElement("must_not_esrs");		
				if(esrGeneralProperties.must_not_esr!=null){
					for(int i: esrGeneralProperties.must_not_esr){
						Element mustNotEsr = doc.createElement("must_not_esr");
						mustNotEsr.appendChild(doc.createTextNode(Integer.toString(i)));
						mustNotEsrs.appendChild(mustNotEsr);
					}
				}
				esr.appendChild(mustNotEsrs);
				
				Element esrCosts = doc.createElement("esr_costs");		
				if(esrCostsArray!=null && esrCostsArray.length!=0){
					for(EsrCostStruct esrCost : esrCostsArray){
						Element esrCostElement = doc.createElement("esr_cost");
						esrCostElement.setAttribute("mincapacity", Double.toString(esrCost.mincapacity));
						esrCostElement.setAttribute("maxcapacity", Double.toString(esrCost.maxcapacity));
						esrCostElement.setAttribute("basecost", Double.toString(esrCost.basecost));
						esrCostElement.setAttribute("unitcost", Double.toString(esrCost.unitcost));
						
						esrCosts.appendChild(esrCostElement);
					}
				}
				esr.appendChild(esrCosts);
			}
			
			PumpGeneralStruct pumpGeneralProperties = gson.fromJson(request.getParameter("pumpGeneral"), PumpGeneralStruct.class);
			PumpManualStruct[] pumpManualArray = gson.fromJson(request.getParameter("pumpManual"), PumpManualStruct[].class);
			
			if(pumpGeneralProperties!=null){
				Element pump = doc.createElement("pump");
				rootElement.appendChild(pump);
				
				Element pumpEnabled = doc.createElement("pump_enabled");
				pumpEnabled.appendChild(doc.createTextNode(Boolean.toString(pumpGeneralProperties.pump_enabled)));
				pump.appendChild(pumpEnabled);
				
				Element minimumPumpSize = doc.createElement("minimum_pump_size");
				minimumPumpSize.appendChild(doc.createTextNode(Double.toString(pumpGeneralProperties.minpumpsize)));
				pump.appendChild(minimumPumpSize);
				
				Element pumpEfficiency = doc.createElement("pump_efficiency");
				pumpEfficiency.appendChild(doc.createTextNode(Double.toString(pumpGeneralProperties.efficiency)));
				pump.appendChild(pumpEfficiency);
				
				Element capitalCost = doc.createElement("capital_cost_per_kw");
				capitalCost.appendChild(doc.createTextNode(Double.toString(pumpGeneralProperties.capitalcost_per_kw)));
				pump.appendChild(capitalCost);
				
				Element energyCost = doc.createElement("energy_cost_per_kwh");
				energyCost.appendChild(doc.createTextNode(Double.toString(pumpGeneralProperties.energycost_per_kwh)));
				pump.appendChild(energyCost);
				
				Element designLifetime = doc.createElement("design_lifetime");
				designLifetime.appendChild(doc.createTextNode(Integer.toString(pumpGeneralProperties.design_lifetime)));
				pump.appendChild(designLifetime);
				
				Element discountRate = doc.createElement("discount_rate");
				discountRate.appendChild(doc.createTextNode(Double.toString(pumpGeneralProperties.discount_rate)));
				pump.appendChild(discountRate);
				
				Element inflationRate = doc.createElement("inflation_rate");
				inflationRate.appendChild(doc.createTextNode(Double.toString(pumpGeneralProperties.inflation_rate)));
				pump.appendChild(inflationRate);
							
				Element mustNotPumps = doc.createElement("pipes_without_pumps");		
				if(pumpGeneralProperties.must_not_pump!=null){
					for(int i: pumpGeneralProperties.must_not_pump){
						Element mustNotPump = doc.createElement("pipe_without_pump");
						mustNotPump.appendChild(doc.createTextNode(Integer.toString(i)));
						mustNotPumps.appendChild(mustNotPump);
					}
				}
				pump.appendChild(mustNotPumps);
				
				Element pumpManual = doc.createElement("manual_pumps");		
				if(pumpManualArray!=null && pumpManualArray.length!=0){
					for(PumpManualStruct p : pumpManualArray){
						Element pumpManualElement = doc.createElement("manual_pump");
						pumpManualElement.setAttribute("pipeid", Integer.toString(p.pipeid));
						pumpManualElement.setAttribute("pumppower", Double.toString(p.pumppower));
						
						pumpManual.appendChild(pumpManualElement);
					}
				}
				pump.appendChild(pumpManual);
			}
			
			ValveStruct[] valves = gson.fromJson(request.getParameter("valves"), ValveStruct[].class);
			if(valves!=null && valves.length>0){
				Element valvesElement = doc.createElement("valves");
				rootElement.appendChild(valvesElement);
				
				for(ValveStruct v : valves){
					Element valveElement = doc.createElement("valve");
					valveElement.setAttribute("pipeid", Integer.toString(v.pipeid));
					valveElement.setAttribute("valvesetting", Double.toString(v.valvesetting));
					
					valvesElement.appendChild(valveElement);
				}
			}
			
			Element map = doc.createElement("map");
			rootElement.appendChild(map);
			
			String mapSourceNode = request.getParameter("mapsource");
			Element mapSourceNodeElement = doc.createElement("map_source_node");
			mapSourceNodeElement.setAttribute("nodeid", mapSourceNode);
			map.appendChild(mapSourceNodeElement);
			
			Element mapNodes = doc.createElement("map_nodes");
			map.appendChild(mapNodes);
			
			MapNodeStruct[] mapNodesArray = gson.fromJson(request.getParameter("mapnodes"), MapNodeStruct[].class);
			
			if(mapNodesArray!=null && mapNodesArray.length!=0)
			{
				for(MapNodeStruct mapNode : mapNodesArray)
				{
					Element mapNodeElement = doc.createElement("map_node");
					mapNodeElement.setAttribute("nodeid", Integer.toString(mapNode.nodeid));
					mapNodeElement.setAttribute("nodename", mapNode.nodename);
					mapNodeElement.setAttribute("latitude", Double.toString(mapNode.latitude));
					mapNodeElement.setAttribute("longitude", Double.toString(mapNode.longitude));
					mapNodeElement.setAttribute("isesr", Boolean.toString(mapNode.isesr));
					
					mapNodes.appendChild(mapNodeElement);
				}
			}
			
			Element mapPipes = doc.createElement("map_pipes");
			map.appendChild(mapPipes);
			
			MapPipeStruct[] mapPipesArray = gson.fromJson(request.getParameter("mappipes"), MapPipeStruct[].class);
			if(mapPipesArray!=null && mapPipesArray.length!=0)
			{
				for(MapPipeStruct mapPipe : mapPipesArray)
				{
					Element mapPipeElement = doc.createElement("map_pipe");
					mapPipeElement.setAttribute("encodedpath", mapPipe.encodedpath);
					mapPipeElement.setAttribute("originid", Integer.toString(mapPipe.originid));
					mapPipeElement.setAttribute("destinationid", Integer.toString(mapPipe.destinationid));
					mapPipeElement.setAttribute("length", Double.toString(mapPipe.length));
				
					mapPipes.appendChild(mapPipeElement);
				}
			}
			
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
			
			DOMSource source = new DOMSource(doc);
			StreamResult sr = new StreamResult(os);
	 
			transformer.transform(source, sr);		
			
			os.flush();
			os.close();
		} 
		catch (Exception e) 
		{
			System.out.println(e);
		}	
    }
    //generate and upload Excel input network file
    protected void uploadExcelInputFile(HttpServletRequest request, HttpServletResponse response)
    {
    	try
    	{
    		OutputStream os = response.getOutputStream();
    		
			Gson gson = new Gson(); 
	
			GeneralStruct generalProperties = gson.fromJson(request.getParameter("general"), GeneralStruct.class);
			NodeStruct[] nodesArray = gson.fromJson(request.getParameter("nodes"), NodeStruct[].class);
			PipeStruct[] pipesArray = gson.fromJson(request.getParameter("pipes"), PipeStruct[].class);
			CommercialPipeStruct[] costsArray = gson.fromJson(request.getParameter("commercialpipes"), CommercialPipeStruct[].class);
			MapNodeStruct[] mapnodesArray = gson.fromJson(request.getParameter("mapnodes"), MapNodeStruct[].class);
			MapPipeStruct[] mappipesArray = gson.fromJson(request.getParameter("mappipes"), MapPipeStruct[].class);

			if(generalProperties.name_project==null || generalProperties.name_project.isEmpty())
				generalProperties.name_project="JalTantra Project";
			
			String filename = generalProperties.name_project+"_input.xls";
			
			response.setContentType("application/vnd.ms-excel"); // Set up mime type
			response.setHeader("Content-Disposition", "attachment; filename="+filename);
	
			Workbook wb = new HSSFWorkbook();
    	
	    	Font font = wb.createFont();
	    	font.setBold(true);
	    	CellStyle cellStyle = wb.createCellStyle();
	    	cellStyle.setFont(font);
	    	cellStyle.setAlignment(CellStyle.ALIGN_CENTER);
	    	cellStyle.setVerticalAlignment(CellStyle.VERTICAL_CENTER);
	    	cellStyle.setBorderBottom(CellStyle.BORDER_THIN);
	    	cellStyle.setBorderRight(CellStyle.BORDER_THIN);
	    	cellStyle.setBorderLeft(CellStyle.BORDER_THIN);
	    	cellStyle.setBorderTop(CellStyle.BORDER_THIN);
	    	cellStyle.setWrapText(true);
	    	
	    	CellStyle largeBoldCellStyle = wb.createCellStyle();
	    	largeBoldCellStyle.setAlignment(CellStyle.ALIGN_CENTER);
	    	largeBoldCellStyle.setVerticalAlignment(CellStyle.VERTICAL_CENTER);
	    	font = wb.createFont();
	    	font.setBold(true);
	    	font.setFontHeightInPoints((short) 16);
	    	largeBoldCellStyle.setFont(font);
	    	
	    	CellStyle smallBoldCellStyle = wb.createCellStyle();
	    	smallBoldCellStyle.setAlignment(CellStyle.ALIGN_CENTER);
	    	smallBoldCellStyle.setVerticalAlignment(CellStyle.VERTICAL_CENTER);
	    	font = wb.createFont();
	    	font.setBold(true);
	    	font.setFontHeightInPoints((short) 12);
	    	smallBoldCellStyle.setFont(font);
	    	
	    	CellStyle integerStyle = wb.createCellStyle();
			DataFormat integerFormat = wb.createDataFormat();
			integerStyle.setDataFormat(integerFormat.getFormat("#,##0"));
			integerStyle.setAlignment(CellStyle.ALIGN_CENTER);
			integerStyle.setVerticalAlignment(CellStyle.VERTICAL_CENTER);
			integerStyle.setBorderBottom(CellStyle.BORDER_THIN);
			integerStyle.setBorderRight(CellStyle.BORDER_THIN);
			integerStyle.setBorderLeft(CellStyle.BORDER_THIN);
			integerStyle.setBorderTop(CellStyle.BORDER_THIN);
			
			CellStyle doubleStyle = wb.createCellStyle();
			DataFormat doubleFormat = wb.createDataFormat();
			doubleStyle.setDataFormat(doubleFormat.getFormat("#,##0.00"));
			doubleStyle.setAlignment(CellStyle.ALIGN_CENTER);
			doubleStyle.setVerticalAlignment(CellStyle.VERTICAL_CENTER);
			doubleStyle.setBorderBottom(CellStyle.BORDER_THIN);
			doubleStyle.setBorderRight(CellStyle.BORDER_THIN);
			doubleStyle.setBorderLeft(CellStyle.BORDER_THIN);
			doubleStyle.setBorderTop(CellStyle.BORDER_THIN);
	    	
	    	Sheet generalSheet = wb.createSheet("General");
	    	
	    	PrintSetup ps = generalSheet.getPrintSetup();
	
	    	ps.setScale((short)80);
	
	    	int rowindex = 0;
	    	int noderowindex , piperowindex, costrowindex, esrgeneralrowindex, esrcostrowindex, pumpgeneralrowindex, pumpmanualrowindex, valverowindex, mapnoderowindex, mappiperowindex;
	    	int generalstart, nodestart , pipestart, coststart, esrgeneralstart, esrcoststart, pumpgeneralstart, pumpmanualstart, valvestart, mapnodestart, mappipestart;
	    	
	    	Row row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,10));
	    	rowindex++;
	    	Cell generalCell = row.createCell(0);
	    	generalCell.setCellValue("JalTantra: System For Optimization of Piped Water Networks, version:"+version);
	    	generalCell.setCellStyle(largeBoldCellStyle);
	    	row.setHeightInPoints((float) 21.75);
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,10));
	    	rowindex++;
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue("developed by CSE and CTARA departments of IIT Bombay");
	    	generalCell.setCellStyle(largeBoldCellStyle);
	    	row.setHeightInPoints((float) 21.75);
	    	
	    	
	    	Date date = new Date();
	    	DateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm a");
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,10));
	    	rowindex++;
	    	
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue(dateFormat.format(date));
	    	generalCell.setCellStyle(smallBoldCellStyle);
	    	
	    	rowindex = rowindex + 2;
	        if(rowindex%2!=0)
	        	rowindex++;
	    	generalstart = rowindex;
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,3,5));
	    	rowindex++;
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue("Network Name");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	generalCell.setCellValue(generalProperties.name_project);
	    	CellStyle tempCellStyle = wb.createCellStyle();
	    	tempCellStyle.cloneStyleFrom(cellStyle);
	    	font = wb.createFont();
	    	font.setBold(true);
	    	font.setFontHeightInPoints((short) 12);
	    	tempCellStyle.setFont(font);
	    	generalCell.setCellStyle(tempCellStyle);
	    	generalCell = row.createCell(4);
	    	generalCell.setCellStyle(tempCellStyle);
	    	generalCell = row.createCell(5);
	    	generalCell.setCellStyle(tempCellStyle);
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	rowindex++;
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue("Organization Name");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	generalCell.setCellValue(generalProperties.name_organization);
	    	generalCell.setCellStyle(integerStyle);
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	rowindex++;
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue("Minimum Node Pressure");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	generalCell.setCellValue(generalProperties.min_node_pressure);
	    	generalCell.setCellStyle(integerStyle);
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	rowindex++;
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue("Default Pipe Roughness 'C'");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	generalCell.setCellValue(generalProperties.def_pipe_roughness);
	    	generalCell.setCellStyle(integerStyle);
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	rowindex++;
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue("Minimum Headloss per KM");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	generalCell.setCellValue(generalProperties.min_hl_perkm);
	    	generalCell.setCellStyle(doubleStyle);
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	rowindex++;
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue("Maximum Headloss per KM");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	generalCell.setCellValue(generalProperties.max_hl_perkm);
	    	generalCell.setCellStyle(doubleStyle);
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	rowindex++;
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue("Maximum Water Speed");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	if(generalProperties.max_water_speed > 0){
	    		generalCell.setCellValue(generalProperties.max_water_speed);		
	    	}
	    	generalCell.setCellStyle(doubleStyle);
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	rowindex++;
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue("Maximum Pipe Pressure");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	if(generalProperties.max_pipe_pressure > 0){
	    		generalCell.setCellValue(generalProperties.max_pipe_pressure);		
	    	}
	    	generalCell.setCellStyle(doubleStyle);
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	rowindex++;
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue("Number of Supply Hours");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	generalCell.setCellValue(generalProperties.supply_hours);
	    	generalCell.setCellStyle(integerStyle);
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	rowindex++;
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue("Source Node ID");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	generalCell.setCellValue(generalProperties.source_nodeid);
	    	generalCell.setCellStyle(integerStyle);
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	rowindex++;
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue("Source Node Name");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	generalCell.setCellValue(generalProperties.source_nodename);
	    	generalCell.setCellStyle(integerStyle);
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	rowindex++;
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue("Source Elevation");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	generalCell.setCellValue(generalProperties.source_elevation);
	    	generalCell.setCellStyle(integerStyle);
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	rowindex++;
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue("Source Head");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	generalCell.setCellValue(generalProperties.source_head);
	    	generalCell.setCellStyle(integerStyle);
	    		    	
	    	
	    	Sheet nodeSheet = generalSheet;//wb.createSheet("Nodes");
	        noderowindex = rowindex + 2;
	        if(noderowindex%2==0)
	        	noderowindex++;
	        
	        row = nodeSheet.createRow(noderowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(noderowindex,noderowindex,0,4));
	        noderowindex++;
	        Cell nodeCell = row.createCell(0);
	        nodeCell.setCellValue("NODE DATA");
	        nodeCell.setCellStyle(cellStyle);
	    	for(int i=1;i<5;i++)
	    	{
	    		nodeCell = row.createCell(i);
	    		nodeCell.setCellStyle(cellStyle);
	    	}
	    	nodestart = noderowindex;
	        Row nodeTitleRow = nodeSheet.createRow(noderowindex);
	        noderowindex++;
	        nodeCell = nodeTitleRow.createCell(0);
	        nodeCell.setCellValue("Node ID");
	        nodeCell.setCellStyle(cellStyle);
	        nodeCell = nodeTitleRow.createCell(1);
	        nodeCell.setCellValue("Node Name");
	        nodeCell.setCellStyle(cellStyle);
	        nodeCell = nodeTitleRow.createCell(2);
	        nodeCell.setCellValue("Elevation");
	        nodeCell.setCellStyle(cellStyle);
	        nodeCell = nodeTitleRow.createCell(3);
	        nodeCell.setCellValue("Demand");
	        nodeCell.setCellStyle(cellStyle);
	        nodeCell = nodeTitleRow.createCell(4);
	        nodeCell.setCellValue("Min. Pressure");
	        nodeCell.setCellStyle(cellStyle);
	        
	        for(NodeStruct node : nodesArray)
			{
	        	if(node.nodeid==generalProperties.source_nodeid)
	        		continue;
	        	
				Row nodeRow = nodeSheet.createRow(noderowindex);
		        noderowindex++;
				
				nodeCell = nodeRow.createCell(0);
				nodeCell.setCellValue(node.nodeid);
				nodeCell.setCellStyle(integerStyle);
				
				nodeCell = nodeRow.createCell(1);
				nodeCell.setCellValue(node.nodename);
				nodeCell.setCellStyle(integerStyle);
				
				nodeCell = nodeRow.createCell(2);
				nodeCell.setCellValue(node.elevation);
				nodeCell.setCellStyle(integerStyle);
				
				nodeCell = nodeRow.createCell(3);
				if(node.demand!=0)
					nodeCell.setCellValue(node.demand);
				nodeCell.setCellStyle(doubleStyle);
				
				nodeCell = nodeRow.createCell(4);
				if(node.minpressure!=0)
					nodeCell.setCellValue(node.minpressure);
				nodeCell.setCellStyle(integerStyle);
			}
	        
	        Sheet pipeSheet = generalSheet;// wb.createSheet("Pipes");
	        
	        piperowindex = noderowindex + 2;
	        if(piperowindex%2==0)
	        	piperowindex++;
	        row = generalSheet.createRow(piperowindex);
	        generalSheet.addMergedRegion(new CellRangeAddress(piperowindex,piperowindex,0,6));
	        piperowindex++;
	        Cell pipeCell = row.createCell(0);
	        pipeCell.setCellValue("PIPE DATA");
	        pipeCell.setCellStyle(cellStyle);
	    	for(int i=1;i<7;i++)
	    	{
	    		pipeCell = row.createCell(i);
	    		pipeCell.setCellStyle(cellStyle);
	    	}
	        pipestart = piperowindex;
	        Row pipeTitleRow = pipeSheet.createRow(piperowindex);
	        piperowindex++;
	        pipeCell = pipeTitleRow.createCell(0);
	        pipeCell.setCellValue("Pipe ID");
	        pipeCell.setCellStyle(cellStyle);
	        pipeCell = pipeTitleRow.createCell(1);
	        pipeCell.setCellValue("Start Node");
	        pipeCell.setCellStyle(cellStyle);
	        pipeCell = pipeTitleRow.createCell(2);
	        pipeCell.setCellValue("End Node");
	        pipeCell.setCellStyle(cellStyle);
	        pipeCell = pipeTitleRow.createCell(3);
	        pipeCell.setCellValue("Length");
	        pipeCell.setCellStyle(cellStyle);
	        pipeCell = pipeTitleRow.createCell(4);
	        pipeCell.setCellValue("Diameter");
	        pipeCell.setCellStyle(cellStyle);
	        pipeCell = pipeTitleRow.createCell(5);
	        pipeCell.setCellValue("Roughness 'C'");
	        pipeCell.setCellStyle(cellStyle);
	        pipeCell = pipeTitleRow.createCell(6);
	        pipeCell.setCellValue("Parallel Allowed");
	        pipeCell.setCellStyle(cellStyle);
	        
	        for(PipeStruct pipe : pipesArray)
			{
				Row pipeRow = pipeSheet.createRow(piperowindex);
		        piperowindex++;
		    
	        	pipeCell = pipeRow.createCell(0);
				pipeCell.setCellValue(pipe.pipeid);
				pipeCell.setCellStyle(integerStyle);
				
				pipeCell = pipeRow.createCell(1);
				pipeCell.setCellValue(pipe.startnode);
				pipeCell.setCellStyle(integerStyle);
				
				pipeCell = pipeRow.createCell(2);
				pipeCell.setCellValue(pipe.endnode);
				pipeCell.setCellStyle(integerStyle);
				
				pipeCell = pipeRow.createCell(3);
				pipeCell.setCellValue(pipe.length);
				pipeCell.setCellStyle(integerStyle);
				
				pipeCell = pipeRow.createCell(4);
				if(pipe.diameter!=0)
					pipeCell.setCellValue(pipe.diameter);
				pipeCell.setCellStyle(integerStyle);
				
				pipeCell = pipeRow.createCell(5);
				if(pipe.roughness!=0)
					pipeCell.setCellValue(pipe.roughness);
				pipeCell.setCellStyle(integerStyle);
				
				pipeCell = pipeRow.createCell(6);
				pipeCell.setCellStyle(integerStyle);
				if(pipe.parallelallowed)
					pipeCell.setCellValue(pipe.parallelallowed);
			}
			
		    Sheet costSheet = generalSheet; //wb.createSheet("Cost");
		    
		    costrowindex = piperowindex + 2;
		    if(costrowindex%2==0)
		    	costrowindex++;
		    row = generalSheet.createRow(costrowindex);
		    generalSheet.addMergedRegion(new CellRangeAddress(costrowindex,costrowindex,0,2));
		    costrowindex++;
	        Cell costCell = row.createCell(0);
	        costCell.setCellValue("COMMERCIAL PIPE DATA");
	        costCell.setCellStyle(cellStyle);
	    	for(int i=1;i<3;i++)
	    	{
	    		costCell = row.createCell(i);
	    		costCell.setCellStyle(cellStyle);
	    	}
		    coststart = costrowindex;
	    	Row costTitleRow = costSheet.createRow(costrowindex);
	    	costrowindex++;
	    	costCell = costTitleRow.createCell(0);
	    	costCell.setCellValue("Diameter");
	    	costCell.setCellStyle(cellStyle);
	    	costCell = costTitleRow.createCell(1);
	    	costCell.setCellValue("Roughness");
	    	costCell.setCellStyle(cellStyle);
	    	costCell = costTitleRow.createCell(2);
	    	costCell.setCellValue("Cost");
	    	costCell.setCellStyle(cellStyle);
	    	
			for(CommercialPipeStruct commercialpipe : costsArray)
			{
				Row costRow = costSheet.createRow(costrowindex);
		    	costrowindex++;
				
				costCell = costRow.createCell(0);
				costCell.setCellValue(commercialpipe.diameter);
				costCell.setCellStyle(integerStyle);
				
				costCell = costRow.createCell(1);
				if(commercialpipe.roughness!=0)
					costCell.setCellValue(commercialpipe.roughness);
				costCell.setCellStyle(integerStyle);
				
				costCell = costRow.createCell(2);
				costCell.setCellValue(commercialpipe.cost);
				costCell.setCellStyle(integerStyle);
			}
			
			EsrGeneralStruct esrGeneralProperties = gson.fromJson(request.getParameter("esrGeneral"), EsrGeneralStruct.class);
			EsrCostStruct[] esrCostsArray = gson.fromJson(request.getParameter("esrCost"), EsrCostStruct[].class);
			
			Sheet esrGeneralSheet = generalSheet;
			
			esrgeneralrowindex = costrowindex + 2;
		    if(esrgeneralrowindex%2==0)
		    	esrgeneralrowindex++;
		    row = esrGeneralSheet.createRow(esrgeneralrowindex);
		    esrGeneralSheet.addMergedRegion(new CellRangeAddress(esrgeneralrowindex,esrgeneralrowindex,0,3));
		    esrgeneralrowindex++;
	        Cell esrGeneralCell = row.createCell(0);
	        esrGeneralCell.setCellValue("ESR GENERAL DATA");
	        esrGeneralCell.setCellStyle(cellStyle);
	    	for(int i=1;i<4;i++)
	    	{
	    		esrGeneralCell = row.createCell(i);
	    		esrGeneralCell.setCellStyle(cellStyle);
	    	}
		    esrgeneralstart = esrgeneralrowindex;
		    
		    row = esrGeneralSheet.createRow(esrgeneralrowindex);
		    esrGeneralSheet.addMergedRegion(new CellRangeAddress(esrgeneralrowindex,esrgeneralrowindex,0,2));
		    esrgeneralrowindex++;
		    esrGeneralCell = row.createCell(0);
		    esrGeneralCell.setCellValue("ESR Enabled");
		    esrGeneralCell.setCellStyle(cellStyle);
		    esrGeneralCell = row.createCell(1);
		    esrGeneralCell.setCellStyle(cellStyle);
		    esrGeneralCell = row.createCell(2);
		    esrGeneralCell.setCellStyle(cellStyle);
		    esrGeneralCell = row.createCell(3);
		    esrGeneralCell.setCellValue(esrGeneralProperties.esr_enabled);
		    esrGeneralCell.setCellStyle(integerStyle);
		    
		    row = esrGeneralSheet.createRow(esrgeneralrowindex);
		    esrGeneralSheet.addMergedRegion(new CellRangeAddress(esrgeneralrowindex,esrgeneralrowindex,0,2));
		    esrgeneralrowindex++;
		    esrGeneralCell = row.createCell(0);
		    esrGeneralCell.setCellValue("Secondary Supply Hours");
		    esrGeneralCell.setCellStyle(cellStyle);
		    esrGeneralCell = row.createCell(1);
		    esrGeneralCell.setCellStyle(cellStyle);
		    esrGeneralCell = row.createCell(2);
		    esrGeneralCell.setCellStyle(cellStyle);
		    esrGeneralCell = row.createCell(3);
		    esrGeneralCell.setCellValue(esrGeneralProperties.secondary_supply_hours);
		    esrGeneralCell.setCellStyle(integerStyle);
		    
		    row = esrGeneralSheet.createRow(esrgeneralrowindex);
		    esrGeneralSheet.addMergedRegion(new CellRangeAddress(esrgeneralrowindex,esrgeneralrowindex,0,2));
		    esrgeneralrowindex++;
		    esrGeneralCell = row.createCell(0);
		    esrGeneralCell.setCellValue("ESR Capacity Factor");
		    esrGeneralCell.setCellStyle(cellStyle);
		    esrGeneralCell = row.createCell(1);
		    esrGeneralCell.setCellStyle(cellStyle);
		    esrGeneralCell = row.createCell(2);
		    esrGeneralCell.setCellStyle(cellStyle);
		    esrGeneralCell = row.createCell(3);
		    esrGeneralCell.setCellValue(esrGeneralProperties.esr_capacity_factor);
		    esrGeneralCell.setCellStyle(doubleStyle);
		    
		    row = esrGeneralSheet.createRow(esrgeneralrowindex);
		    esrGeneralSheet.addMergedRegion(new CellRangeAddress(esrgeneralrowindex,esrgeneralrowindex,0,2));
		    esrgeneralrowindex++;
		    esrGeneralCell = row.createCell(0);
		    esrGeneralCell.setCellValue("Maximum ESR Height");
		    esrGeneralCell.setCellStyle(cellStyle);
		    esrGeneralCell = row.createCell(1);
		    esrGeneralCell.setCellStyle(cellStyle);
		    esrGeneralCell = row.createCell(2);
		    esrGeneralCell.setCellStyle(cellStyle);
		    esrGeneralCell = row.createCell(3);
		    esrGeneralCell.setCellValue(esrGeneralProperties.max_esr_height);
		    esrGeneralCell.setCellStyle(integerStyle);
		    
		    row = esrGeneralSheet.createRow(esrgeneralrowindex);
		    esrGeneralSheet.addMergedRegion(new CellRangeAddress(esrgeneralrowindex,esrgeneralrowindex,0,2));
		    esrgeneralrowindex++;
		    esrGeneralCell = row.createCell(0);
		    esrGeneralCell.setCellValue("Allow ESRs at zero demand nodes");
		    esrGeneralCell.setCellStyle(cellStyle);
		    esrGeneralCell = row.createCell(1);
		    esrGeneralCell.setCellStyle(cellStyle);
		    esrGeneralCell = row.createCell(2);
		    esrGeneralCell.setCellStyle(cellStyle);
		    esrGeneralCell = row.createCell(3);
		    esrGeneralCell.setCellValue(esrGeneralProperties.allow_dummy);
		    esrGeneralCell.setCellStyle(integerStyle);
		    
		    
		    String mustHaveEsrs = "";
		    if(esrGeneralProperties.must_esr!=null){
			    for (int nodeid : esrGeneralProperties.must_esr){
			    	mustHaveEsrs = mustHaveEsrs + nodeid + ";"; 
			    }
			    if(mustHaveEsrs.length()>0){
			    	mustHaveEsrs = mustHaveEsrs.substring(0, mustHaveEsrs.length()-1);
			    }
		    }
		    
		    String mustNotHaveEsrs = "";
		    if(esrGeneralProperties.must_not_esr!=null){
			    for (int nodeid : esrGeneralProperties.must_not_esr){
			    	mustNotHaveEsrs = mustNotHaveEsrs + nodeid + ";"; 
			    }
			    if(mustNotHaveEsrs.length()>0){
			    	mustNotHaveEsrs = mustNotHaveEsrs.substring(0, mustNotHaveEsrs.length()-1);
			    }
		    }
		    
		    row = esrGeneralSheet.createRow(esrgeneralrowindex);
		    esrGeneralSheet.addMergedRegion(new CellRangeAddress(esrgeneralrowindex,esrgeneralrowindex,0,2));
		    esrgeneralrowindex++;
		    esrGeneralCell = row.createCell(0);
		    esrGeneralCell.setCellValue("Nodes that must have ESRs");
		    esrGeneralCell.setCellStyle(cellStyle);
		    esrGeneralCell = row.createCell(1);
		    esrGeneralCell.setCellStyle(cellStyle);
		    esrGeneralCell = row.createCell(2);
		    esrGeneralCell.setCellStyle(cellStyle);
		    esrGeneralCell = row.createCell(3);
		    esrGeneralCell.setCellValue(mustHaveEsrs);
		    esrGeneralCell.setCellStyle(integerStyle);
		    
		    row = esrGeneralSheet.createRow(esrgeneralrowindex);
		    esrGeneralSheet.addMergedRegion(new CellRangeAddress(esrgeneralrowindex,esrgeneralrowindex,0,2));
		    esrgeneralrowindex++;
		    esrGeneralCell = row.createCell(0);
		    esrGeneralCell.setCellValue("Nodes that must not have ESRs");
		    esrGeneralCell.setCellStyle(cellStyle);
		    esrGeneralCell = row.createCell(1);
		    esrGeneralCell.setCellStyle(cellStyle);
		    esrGeneralCell = row.createCell(2);
		    esrGeneralCell.setCellStyle(cellStyle);
		    esrGeneralCell = row.createCell(3);
		    esrGeneralCell.setCellValue(mustNotHaveEsrs);
		    esrGeneralCell.setCellStyle(integerStyle);
		    
		    
		    Sheet esrCostSheet = generalSheet; //wb.createSheet("Cost");
		    
		    esrcostrowindex = esrgeneralrowindex + 2;
		    if(esrcostrowindex%2==0)
		    	esrcostrowindex++;
		    row = generalSheet.createRow(esrcostrowindex);
		    generalSheet.addMergedRegion(new CellRangeAddress(esrcostrowindex,esrcostrowindex,0,3));
		    esrcostrowindex++;
	        Cell esrCostCell = row.createCell(0);
	        esrCostCell.setCellValue("ESR COST DATA");
	        esrCostCell.setCellStyle(cellStyle);
	    	for(int i=1;i<4;i++)
	    	{
	    		esrCostCell = row.createCell(i);
	    		esrCostCell.setCellStyle(cellStyle);
	    	}
		    esrcoststart = esrcostrowindex;
	    	Row esrCostTitleRow = costSheet.createRow(esrcostrowindex);
	    	esrcostrowindex++;
	    	esrCostCell = esrCostTitleRow.createCell(0);
	    	esrCostCell.setCellValue("Minimum Capacity");
	    	esrCostCell.setCellStyle(cellStyle);
	    	esrCostCell = esrCostTitleRow.createCell(1);
	    	esrCostCell.setCellValue("Maximum Capacity");
	    	esrCostCell.setCellStyle(cellStyle);
	    	esrCostCell = esrCostTitleRow.createCell(2);
	    	esrCostCell.setCellValue("Base Cost");
	    	esrCostCell.setCellStyle(cellStyle);
	    	esrCostCell = esrCostTitleRow.createCell(3);
	    	esrCostCell.setCellValue("Unit Cost");
	    	esrCostCell.setCellStyle(cellStyle);
	    	
			for(EsrCostStruct esrCost : esrCostsArray)
			{
				Row esrCostRow = esrCostSheet.createRow(esrcostrowindex);
				esrcostrowindex++;
				
				esrCostCell = esrCostRow.createCell(0);
				esrCostCell.setCellValue(esrCost.mincapacity);
				esrCostCell.setCellStyle(integerStyle);
				
				esrCostCell = esrCostRow.createCell(1);
				esrCostCell.setCellValue(esrCost.maxcapacity);
				esrCostCell.setCellStyle(integerStyle);
				
				esrCostCell = esrCostRow.createCell(2);
				esrCostCell.setCellValue(esrCost.basecost);
				esrCostCell.setCellStyle(integerStyle);
				
				esrCostCell = esrCostRow.createCell(3);
				esrCostCell.setCellValue(esrCost.unitcost);
				esrCostCell.setCellStyle(doubleStyle);
			}
		    
			PumpGeneralStruct pumpGeneralProperties = gson.fromJson(request.getParameter("pumpGeneral"), PumpGeneralStruct.class);
			PumpManualStruct[] pumpManualArray = gson.fromJson(request.getParameter("pumpManual"), PumpManualStruct[].class);
			
			Sheet pumpGeneralSheet = generalSheet;
			
			pumpgeneralrowindex = esrcostrowindex + 2;
		    if(pumpgeneralrowindex%2==0)
		    	pumpgeneralrowindex++;
		    row = pumpGeneralSheet.createRow(pumpgeneralrowindex);
		    pumpGeneralSheet.addMergedRegion(new CellRangeAddress(pumpgeneralrowindex,pumpgeneralrowindex,0,3));
		    pumpgeneralrowindex++;
	        Cell pumpGeneralCell = row.createCell(0);
	        pumpGeneralCell.setCellValue("PUMP GENERAL DATA");
	        pumpGeneralCell.setCellStyle(cellStyle);
	    	for(int i=1;i<4;i++)
	    	{
	    		pumpGeneralCell = row.createCell(i);
	    		pumpGeneralCell.setCellStyle(cellStyle);
	    	}
		    pumpgeneralstart = pumpgeneralrowindex;
		    
		    row = pumpGeneralSheet.createRow(pumpgeneralrowindex);
		    pumpGeneralSheet.addMergedRegion(new CellRangeAddress(pumpgeneralrowindex,pumpgeneralrowindex,0,2));
		    pumpgeneralrowindex++;
		    pumpGeneralCell = row.createCell(0);
		    pumpGeneralCell.setCellValue("Pump Enabled");
		    pumpGeneralCell.setCellStyle(cellStyle);
		    pumpGeneralCell = row.createCell(1);
		    pumpGeneralCell.setCellStyle(cellStyle);
		    pumpGeneralCell = row.createCell(2);
		    pumpGeneralCell.setCellStyle(cellStyle);
		    pumpGeneralCell = row.createCell(3);
		    pumpGeneralCell.setCellValue(pumpGeneralProperties.pump_enabled);
		    pumpGeneralCell.setCellStyle(integerStyle);
		    
		    row = pumpGeneralSheet.createRow(pumpgeneralrowindex);
		    pumpGeneralSheet.addMergedRegion(new CellRangeAddress(pumpgeneralrowindex,pumpgeneralrowindex,0,2));
		    pumpgeneralrowindex++;
		    pumpGeneralCell = row.createCell(0);
		    pumpGeneralCell.setCellValue("Minimum Pump Size");
		    pumpGeneralCell.setCellStyle(cellStyle);
		    pumpGeneralCell = row.createCell(1);
		    pumpGeneralCell.setCellStyle(cellStyle);
		    pumpGeneralCell = row.createCell(2);
		    pumpGeneralCell.setCellStyle(cellStyle);
		    pumpGeneralCell = row.createCell(3);
		    pumpGeneralCell.setCellValue(pumpGeneralProperties.minpumpsize);
		    pumpGeneralCell.setCellStyle(integerStyle);
		    
		    row = pumpGeneralSheet.createRow(pumpgeneralrowindex);
		    pumpGeneralSheet.addMergedRegion(new CellRangeAddress(pumpgeneralrowindex,pumpgeneralrowindex,0,2));
		    pumpgeneralrowindex++;
		    pumpGeneralCell = row.createCell(0);
		    pumpGeneralCell.setCellValue("Pump Efficiency");
		    pumpGeneralCell.setCellStyle(cellStyle);
		    pumpGeneralCell = row.createCell(1);
		    pumpGeneralCell.setCellStyle(cellStyle);
		    pumpGeneralCell = row.createCell(2);
		    pumpGeneralCell.setCellStyle(cellStyle);
		    pumpGeneralCell = row.createCell(3);
		    pumpGeneralCell.setCellValue(pumpGeneralProperties.efficiency);
		    pumpGeneralCell.setCellStyle(integerStyle);
		    
		    row = pumpGeneralSheet.createRow(pumpgeneralrowindex);
		    pumpGeneralSheet.addMergedRegion(new CellRangeAddress(pumpgeneralrowindex,pumpgeneralrowindex,0,2));
		    pumpgeneralrowindex++;
		    pumpGeneralCell = row.createCell(0);
		    pumpGeneralCell.setCellValue("Capital Cost per kW");
		    pumpGeneralCell.setCellStyle(cellStyle);
		    pumpGeneralCell = row.createCell(1);
		    pumpGeneralCell.setCellStyle(cellStyle);
		    pumpGeneralCell = row.createCell(2);
		    pumpGeneralCell.setCellStyle(cellStyle);
		    pumpGeneralCell = row.createCell(3);
		    pumpGeneralCell.setCellValue(pumpGeneralProperties.capitalcost_per_kw);
		    pumpGeneralCell.setCellStyle(integerStyle);
		    
		    row = pumpGeneralSheet.createRow(pumpgeneralrowindex);
		    pumpGeneralSheet.addMergedRegion(new CellRangeAddress(pumpgeneralrowindex,pumpgeneralrowindex,0,2));
		    pumpgeneralrowindex++;
		    pumpGeneralCell = row.createCell(0);
		    pumpGeneralCell.setCellValue("Energy Cost per kWh");
		    pumpGeneralCell.setCellStyle(cellStyle);
		    pumpGeneralCell = row.createCell(1);
		    pumpGeneralCell.setCellStyle(cellStyle);
		    pumpGeneralCell = row.createCell(2);
		    pumpGeneralCell.setCellStyle(cellStyle);
		    pumpGeneralCell = row.createCell(3);
		    pumpGeneralCell.setCellValue(pumpGeneralProperties.energycost_per_kwh);
		    pumpGeneralCell.setCellStyle(doubleStyle);
		    
		    row = pumpGeneralSheet.createRow(pumpgeneralrowindex);
		    pumpGeneralSheet.addMergedRegion(new CellRangeAddress(pumpgeneralrowindex,pumpgeneralrowindex,0,2));
		    pumpgeneralrowindex++;
		    pumpGeneralCell = row.createCell(0);
		    pumpGeneralCell.setCellValue("Design Lifetime");
		    pumpGeneralCell.setCellStyle(cellStyle);
		    pumpGeneralCell = row.createCell(1);
		    pumpGeneralCell.setCellStyle(cellStyle);
		    pumpGeneralCell = row.createCell(2);
		    pumpGeneralCell.setCellStyle(cellStyle);
		    pumpGeneralCell = row.createCell(3);
		    pumpGeneralCell.setCellValue(pumpGeneralProperties.design_lifetime);
		    pumpGeneralCell.setCellStyle(integerStyle);
		    
		    row = pumpGeneralSheet.createRow(pumpgeneralrowindex);
		    pumpGeneralSheet.addMergedRegion(new CellRangeAddress(pumpgeneralrowindex,pumpgeneralrowindex,0,2));
		    pumpgeneralrowindex++;
		    pumpGeneralCell = row.createCell(0);
		    pumpGeneralCell.setCellValue("Discount Rate");
		    pumpGeneralCell.setCellStyle(cellStyle);
		    pumpGeneralCell = row.createCell(1);
		    pumpGeneralCell.setCellStyle(cellStyle);
		    pumpGeneralCell = row.createCell(2);
		    pumpGeneralCell.setCellStyle(cellStyle);
		    pumpGeneralCell = row.createCell(3);
		    pumpGeneralCell.setCellValue(pumpGeneralProperties.discount_rate);
		    pumpGeneralCell.setCellStyle(doubleStyle);
		    
		    row = pumpGeneralSheet.createRow(pumpgeneralrowindex);
		    pumpGeneralSheet.addMergedRegion(new CellRangeAddress(pumpgeneralrowindex,pumpgeneralrowindex,0,2));
		    pumpgeneralrowindex++;
		    pumpGeneralCell = row.createCell(0);
		    pumpGeneralCell.setCellValue("Inflation Rate");
		    pumpGeneralCell.setCellStyle(cellStyle);
		    pumpGeneralCell = row.createCell(1);
		    pumpGeneralCell.setCellStyle(cellStyle);
		    pumpGeneralCell = row.createCell(2);
		    pumpGeneralCell.setCellStyle(cellStyle);
		    pumpGeneralCell = row.createCell(3);
		    pumpGeneralCell.setCellValue(pumpGeneralProperties.inflation_rate);
		    pumpGeneralCell.setCellStyle(doubleStyle);
		    		    
		    String mustNotHavePumps = "";
		    if(pumpGeneralProperties.must_not_pump!=null){
			    for (int pipeid : pumpGeneralProperties.must_not_pump){
			    	mustNotHavePumps = mustNotHavePumps + pipeid + ";"; 
			    }
			    if(mustNotHavePumps.length()>0){
			    	mustNotHavePumps = mustNotHavePumps.substring(0, mustNotHavePumps.length()-1);
			    }
		    }
		    
		    row = pumpGeneralSheet.createRow(pumpgeneralrowindex);
		    pumpGeneralSheet.addMergedRegion(new CellRangeAddress(pumpgeneralrowindex,pumpgeneralrowindex,0,2));
		    pumpgeneralrowindex++;
		    pumpGeneralCell = row.createCell(0);
		    pumpGeneralCell.setCellValue("Pipes that must not have Pumps");
		    pumpGeneralCell.setCellStyle(cellStyle);
		    pumpGeneralCell = row.createCell(1);
		    pumpGeneralCell.setCellStyle(cellStyle);
		    pumpGeneralCell = row.createCell(2);
		    pumpGeneralCell.setCellStyle(cellStyle);
		    pumpGeneralCell = row.createCell(3);
		    pumpGeneralCell.setCellValue(mustNotHavePumps);
		    pumpGeneralCell.setCellStyle(integerStyle);
		    		    
		    Sheet pumpManualSheet = generalSheet; //wb.createSheet("Cost");
		    
		    pumpmanualrowindex = pumpgeneralrowindex + 2;
		    if(pumpmanualrowindex%2==0)
		    	pumpmanualrowindex++;
		    row = pumpManualSheet.createRow(pumpmanualrowindex);
		    pumpManualSheet.addMergedRegion(new CellRangeAddress(pumpmanualrowindex,pumpmanualrowindex,0,1));
		    pumpmanualrowindex++;
	        Cell pumpManualCell = row.createCell(0);
	        pumpManualCell.setCellValue("MANUAL PUMPS DATA");
	        pumpManualCell.setCellStyle(cellStyle);
	    	for(int i=1;i<2;i++){
	    		pumpManualCell = row.createCell(i);
	    		pumpManualCell.setCellStyle(cellStyle);
	    	}
		    pumpmanualstart = pumpmanualrowindex;
	    	Row pumpManualTitleRow = pumpManualSheet.createRow(pumpmanualrowindex);
	    	pumpmanualrowindex++;
	    	pumpManualCell = pumpManualTitleRow.createCell(0);
	    	pumpManualCell.setCellValue("Pipe ID");
	    	pumpManualCell.setCellStyle(cellStyle);
	    	pumpManualCell = pumpManualTitleRow.createCell(1);
	    	pumpManualCell.setCellValue("Pump Power");
	    	pumpManualCell.setCellStyle(cellStyle);
	    	
			for(PumpManualStruct p : pumpManualArray){
				Row pumpManualRow = pumpManualSheet.createRow(pumpmanualrowindex);
				pumpmanualrowindex++;
				
				pumpManualCell = pumpManualRow.createCell(0);
				pumpManualCell.setCellValue(p.pipeid);
				pumpManualCell.setCellStyle(integerStyle);
				
				pumpManualCell = pumpManualRow.createCell(1);
				pumpManualCell.setCellValue(p.pumppower);
				pumpManualCell.setCellStyle(integerStyle);
			}
		    
			ValveStruct[] valves = gson.fromJson(request.getParameter("valves"), ValveStruct[].class);
			
			Sheet valveSheet = generalSheet; //wb.createSheet("Cost");
		    
		    valverowindex = pumpmanualrowindex + 2;
		    if(valverowindex%2==0)
		    	valverowindex++;
		    row = valveSheet.createRow(valverowindex);
		    valveSheet.addMergedRegion(new CellRangeAddress(valverowindex,valverowindex,0,1));
		    valverowindex++;
	        Cell valveCell = row.createCell(0);
	        valveCell.setCellValue("VALVES DATA");
	        valveCell.setCellStyle(cellStyle);
	    	for(int i=1;i<2;i++){
	    		valveCell = row.createCell(i);
	    		valveCell.setCellStyle(cellStyle);
	    	}
		    valvestart = valverowindex;
	    	Row valveTitleRow = valveSheet.createRow(valverowindex);
	    	valverowindex++;
	    	valveCell = valveTitleRow.createCell(0);
	    	valveCell.setCellValue("Pipe ID");
	    	valveCell.setCellStyle(cellStyle);
	    	valveCell = valveTitleRow.createCell(1);
	    	valveCell.setCellValue("Valve Setting");
	    	valveCell.setCellStyle(cellStyle);
	    	
			for(ValveStruct v : valves){
				Row valveRow = valveSheet.createRow(valverowindex);
				valverowindex++;
				
				valveCell = valveRow.createCell(0);
				valveCell.setCellValue(v.pipeid);
				valveCell.setCellStyle(integerStyle);
				
				valveCell = valveRow.createCell(1);
				valveCell.setCellValue(v.valvesetting);
				valveCell.setCellStyle(integerStyle);
			}
			
			Sheet mapNodeSheet = generalSheet;
		    
		    mapnoderowindex = valverowindex + 2;
		    if(mapnoderowindex%2==1)
		    	mapnoderowindex++;
		    
		    String mapSourceNode = request.getParameter("mapsource");
	    	row = generalSheet.createRow(mapnoderowindex);
	    	mapnoderowindex++;
	    	Cell mapnodeCell = row.createCell(0);
	        mapnodeCell.setCellValue("Map Source Node");
	        mapnodeCell.setCellStyle(cellStyle);
	        mapnodeCell = row.createCell(1);
	        mapnodeCell.setCellValue(mapSourceNode);
	        mapnodeCell.setCellStyle(integerStyle);
	        
		    row = generalSheet.createRow(mapnoderowindex);
		    generalSheet.addMergedRegion(new CellRangeAddress(mapnoderowindex,mapnoderowindex,0,4));
		    mapnoderowindex++;
	        mapnodeCell = row.createCell(0);
	        mapnodeCell.setCellValue("MAP NODE DATA");
	        mapnodeCell.setCellStyle(cellStyle);
	    	for(int i=1;i<5;i++)
	    	{
	    		mapnodeCell = row.createCell(i);
	    		mapnodeCell.setCellStyle(cellStyle);
	    	}
	    		    	
		    mapnodestart = mapnoderowindex;
	    	Row mapnodeTitleRow = costSheet.createRow(mapnoderowindex);
	    	mapnoderowindex++;
	    	mapnodeCell = mapnodeTitleRow.createCell(0);
	    	mapnodeCell.setCellValue("Node Name");
	    	mapnodeCell.setCellStyle(cellStyle);
	    	mapnodeCell = mapnodeTitleRow.createCell(1);
	    	mapnodeCell.setCellValue("Node ID");
	    	mapnodeCell.setCellStyle(cellStyle);
	    	mapnodeCell = mapnodeTitleRow.createCell(2);
	    	mapnodeCell.setCellValue("Latitude");
	    	mapnodeCell.setCellStyle(cellStyle);
	    	mapnodeCell = mapnodeTitleRow.createCell(3);
	    	mapnodeCell.setCellValue("Longitude");
	    	mapnodeCell.setCellStyle(cellStyle);
	    	mapnodeCell = mapnodeTitleRow.createCell(4);
	    	mapnodeCell.setCellValue("Is ESR");
	    	mapnodeCell.setCellStyle(cellStyle);
	    	
			for(MapNodeStruct mapnode : mapnodesArray)
			{
				Row mapnodeRow = mapNodeSheet.createRow(mapnoderowindex);
				mapnoderowindex++;
				
				mapnodeCell = mapnodeRow.createCell(0);
				mapnodeCell.setCellValue(mapnode.nodename);
				mapnodeCell.setCellStyle(cellStyle);
				
				mapnodeCell = mapnodeRow.createCell(1);
				mapnodeCell.setCellValue(mapnode.nodeid);
				mapnodeCell.setCellStyle(integerStyle);
				
				mapnodeCell = mapnodeRow.createCell(2);
				mapnodeCell.setCellValue(mapnode.latitude);
				mapnodeCell.setCellStyle(doubleStyle);
				
				mapnodeCell = mapnodeRow.createCell(3);
				mapnodeCell.setCellValue(mapnode.longitude);
				mapnodeCell.setCellStyle(doubleStyle);
				
				mapnodeCell = mapnodeRow.createCell(4);
				mapnodeCell.setCellValue(mapnode.isesr);
				mapnodeCell.setCellStyle(integerStyle);
			}
			
			Sheet mapPipeSheet = generalSheet;
		    
		    mappiperowindex = mapnoderowindex + 2;
		    if(mappiperowindex%2==0)
		    	mappiperowindex++;
		    row = generalSheet.createRow(mappiperowindex);
		    generalSheet.addMergedRegion(new CellRangeAddress(mappiperowindex,mappiperowindex,0,3));
		    mappiperowindex++;
	        Cell mappipeCell = row.createCell(0);
	        mappipeCell.setCellValue("MAP PIPE DATA");
	        mappipeCell.setCellStyle(cellStyle);
	    	for(int i=1;i<4;i++)
	    	{
	    		mappipeCell = row.createCell(i);
	    		mappipeCell.setCellStyle(cellStyle);
	    	}
		    mappipestart = mappiperowindex;
	    	Row mappipeTitleRow = costSheet.createRow(mappiperowindex);
	    	mappiperowindex++;
	    	mappipeCell = mappipeTitleRow.createCell(0);
	    	mappipeCell.setCellValue("Origin ID");
	    	mappipeCell.setCellStyle(cellStyle);
	    	mappipeCell = mappipeTitleRow.createCell(1);
	    	mappipeCell.setCellValue("Destination ID");
	    	mappipeCell.setCellStyle(cellStyle);
	    	mappipeCell = mappipeTitleRow.createCell(2);
	    	mappipeCell.setCellValue("Length");
	    	mappipeCell.setCellStyle(cellStyle);
	    	mappipeCell = mappipeTitleRow.createCell(3);
	    	mappipeCell.setCellValue("Encoded Path");
	    	mappipeCell.setCellStyle(cellStyle);
	    	
	    	
	    	// adding auto size for 4th column here to avoid resizing encoded path column
	    	generalSheet.autoSizeColumn(3);
	    	
	    	
			for(MapPipeStruct mappipe : mappipesArray)
			{
				Row mappipeRow = mapPipeSheet.createRow(mappiperowindex);
				mappiperowindex++;
				
				mappipeCell = mappipeRow.createCell(0);
				mappipeCell.setCellValue(mappipe.originid);
				mappipeCell.setCellStyle(integerStyle);
				
				mappipeCell = mappipeRow.createCell(1);
				mappipeCell.setCellValue(mappipe.destinationid);
				mappipeCell.setCellStyle(integerStyle);
				
				mappipeCell = mappipeRow.createCell(2);
				mappipeCell.setCellValue(mappipe.length);
				mappipeCell.setCellStyle(doubleStyle);
				
				mappipeCell = mappipeRow.createCell(3);
				mappipeCell.setCellValue(mappipe.encodedpath);
				mappipeCell.setCellStyle(doubleStyle);
			}
						
	    	SheetConditionalFormatting sheetCF = generalSheet.getSheetConditionalFormatting();
	
	        ConditionalFormattingRule rule1 = sheetCF.createConditionalFormattingRule("MOD(ROW(),2)");
	        PatternFormatting fill1 = rule1.createPatternFormatting();
	        fill1.setFillBackgroundColor(IndexedColors.PALE_BLUE.index);
	        fill1.setFillPattern(PatternFormatting.SOLID_FOREGROUND);
	        
	        CellRangeAddress[] regions = {
	                CellRangeAddress.valueOf("A"+generalstart+":D"+rowindex), 
	                CellRangeAddress.valueOf("A"+nodestart+":E"+noderowindex),
	                CellRangeAddress.valueOf("A"+pipestart+":G"+piperowindex),
	                CellRangeAddress.valueOf("A"+coststart+":C"+costrowindex),
	                CellRangeAddress.valueOf("A"+esrgeneralstart+":D"+esrgeneralrowindex),
	                CellRangeAddress.valueOf("A"+esrcoststart+":D"+esrcostrowindex),
	                CellRangeAddress.valueOf("A"+pumpgeneralstart+":D"+pumpgeneralrowindex),
	                CellRangeAddress.valueOf("A"+pumpmanualstart+":B"+pumpmanualrowindex),
	                CellRangeAddress.valueOf("A"+valvestart+":B"+valverowindex),
	                CellRangeAddress.valueOf("A"+mapnodestart+":E"+mapnoderowindex),
	                CellRangeAddress.valueOf("A"+mappipestart+":D"+mappiperowindex),
	        };
	
	        sheetCF.addConditionalFormatting(regions, rule1);
	    	
	        for(int i=0;i<7;i++) {
	        	if(i==3) continue; //4th column resize
	        	generalSheet.autoSizeColumn(i);
	        }
	    	wb.write(os);
	        wb.close();
	        
	        os.flush();
	        os.close();
    	}
    	catch (Exception e) 
		{
			System.out.println(e);
		}
    }
    //generate and upload EPANET output network file
    protected void uploadEpanetOutputFile(HttpServletRequest request, HttpServletResponse response) throws IOException{
    	try{
    		OutputStream os = response.getOutputStream();
    		PrintWriter pw = new PrintWriter(os);
    		
    		Gson gson = new Gson(); 
    		
			GeneralStruct generalProperties = gson.fromJson(request.getParameter("general"), GeneralStruct.class);
			NodeStruct[] resultNodesArray = gson.fromJson(request.getParameter("resultnodes"), NodeStruct[].class);
			PipeStruct[] resultPipesArray = gson.fromJson(request.getParameter("resultpipes"), PipeStruct[].class);
			//CommercialPipeStruct[] resultCostsArray = gson.fromJson(request.getParameter("resultcosts"), CommercialPipeStruct[].class);
			EsrGeneralStruct esrGeneralProperties = gson.fromJson(request.getParameter("esrGeneral"), EsrGeneralStruct.class);
			ResultEsrStruct[] resultEsrsArray = gson.fromJson(request.getParameter("resultesrs"), ResultEsrStruct[].class);
			PumpGeneralStruct pumpGeneralProperties = gson.fromJson(request.getParameter("pumpGeneral"), PumpGeneralStruct.class);
			ResultPumpStruct[] resultPumpsArray = gson.fromJson(request.getParameter("resultpumps"), ResultPumpStruct[].class);
			ValveStruct[] valves = gson.fromJson(request.getParameter("valves"), ValveStruct[].class);
			
			String resultCoordinatesString = request.getParameter("coordinatesstring");
			String resultVerticesString = request.getParameter("verticesstring");
			
			if(generalProperties.name_project==null || generalProperties.name_project.isEmpty())
				generalProperties.name_project="JalTantra Project";
			
			String filename = generalProperties.name_project+"_output.inp";
			
			response.setContentType("text/plain"); // Set up mime type
			response.setHeader("Content-Disposition", "attachment; filename="+filename);
	
			String titleString = "[TITLE]\n"+
				     generalProperties.name_project+"\n\n";
			String endString = "[END]\n";
			String optionString = "[OPTIONS]\n"+
				      "Units\tLPS\n"+
				      "Headloss\tH-W\n\n";
			
			String junctionString = "[JUNCTIONS]\n"+
									";ID\tElev\tDemand\tPattern\n"; // id elev demand pattern			
			String pipeString = "[PIPES]\n"+
							    ";ID\tStart\tEnd\tLength\tDiameter\tRoughness\tMinorLoss\tStatus\n"; //id start end length diameter roughness minorloss status
			String coordinateString = "[COORDINATES]\n"+
							      	  ";Node\tX-Coord\tY-Coord\n";
			String verticesString = "[VERTICES]\n"+
			      	  ";Pipe\tX-Coord\tY-Coord\n";
			String tanksString = "[TANKS]\n"+
			      	  ";ID\tElev\tInitLvl\tMinLvl\tMaxLvl\tDiam\tVolume\n";
			String patternString = "[PATTERNS]\n";
			String timesString = "[TIMES]\nDURATION\t24 HOURS\n";
			String pumpString = "[PUMPS]\n;ID\tNode1\tNode2\tProperties\n";
			String valveString = "[VALVES]\n;ID\tNode1\tNode2\tDiameter\tType\tSetting\n";
			Set<Integer> esrNodes = new HashSet<Integer>(); 
			Set<Integer> esrs = new HashSet<Integer>(); 
			Set<Integer> esrsWithPrimaryChildren = new HashSet<Integer>();
			Map<Integer,ResultPumpStruct> pumppipes = new HashMap<Integer,ResultPumpStruct>();
			Map<Integer,ValveStruct> valvepipes = new HashMap<Integer,ValveStruct>();
			
			
			for(String s: resultCoordinatesString.split(",")){
				if(s!=null &&!s.isEmpty()){
					coordinateString += s +"\n";
					//String[] t = s.trim().split(" ", 2);
					//coord.put(Integer.parseInt(t[0]), t[1]);
				}
			}
			
			for(String s: resultVerticesString.split(",")){
				if(s!=null &&!s.isEmpty()){
					verticesString += s +"\n";
				}
			}
			
			boolean esrenabled = esrGeneralProperties.esr_enabled;
			boolean pumpenabled = pumpGeneralProperties.pump_enabled;
			//NodeStruct source = null;
			
			if(esrenabled){
				for(ResultEsrStruct e : resultEsrsArray){
					double maxlevel = 5;
					double diameter = Math.sqrt(0.004*e.capacity/(Math.PI*maxlevel));
					
					String nodeid = e.nodeid+"t";
					if(e.hasprimarychild){
						junctionString += e.nodeid+"j"+"\t"+e.elevation+"\t"+"0"+"\n";
						esrsWithPrimaryChildren.add(e.nodeid);
					}
					tanksString += nodeid+"\t"+(e.elevation+e.esrheight)+"\t"+maxlevel+"\t"+"0\t"+maxlevel+"\t"+diameter+"0"+"\n";
					esrs.add(e.nodeid);
				}
				
				patternString += "PriPat\t";
				int supplyhours = Math.min(24, (int)Math.ceil(generalProperties.supply_hours));
				int firstphase = (24-supplyhours)/2;
				int secondphase = supplyhours/2;
				int thirdphase = 24 - supplyhours - firstphase;
				int fourthphase = supplyhours - secondphase;
				
				for(int i=0;i<firstphase;i++)
					patternString += "0 ";
				for(int i=0;i<secondphase;i++)
					patternString += "1 ";
				for(int i=0;i<thirdphase;i++)
					patternString += "0 ";
				for(int i=0;i<fourthphase;i++)
					patternString += "1 ";
				patternString += "\n";
				
				patternString += "SecPat\t";
				supplyhours = Math.min(24, (int)Math.ceil(esrGeneralProperties.secondary_supply_hours));
				firstphase = (24-supplyhours)/2;
				secondphase = supplyhours/2;
				thirdphase = 24 - supplyhours - firstphase;
				fourthphase = supplyhours - secondphase;
				
				for(int i=0;i<firstphase;i++)
					patternString += "0 ";
				for(int i=0;i<secondphase;i++)
					patternString += "1 ";
				for(int i=0;i<thirdphase;i++)
					patternString += "0 ";
				for(int i=0;i<fourthphase;i++)
					patternString += "1 ";
				patternString += "\n";
			}
			
			if(pumpenabled){
				for(ResultPumpStruct p : resultPumpsArray){
					pumppipes.put(p.pipeid, p);
				}
			}
			
			for(ValveStruct v : valves){
				valvepipes.put(v.pipeid, v);
			}
			
			String priPat = esrenabled?"\tPriPat":"";

			String reservoirString = "[RESERVOIRS]\n"+
					 ";ID\tHead\tPattern\n"+
					 generalProperties.source_nodeid+"\t"+generalProperties.source_head+priPat+"\n\n"; //id head pattern

			
			for(NodeStruct n : resultNodesArray){
				if(n.nodeid==generalProperties.source_nodeid){
					//source = n;
					continue;
				}
				if(esrs.contains(n.nodeid)){
					if(n.demand>0){
						junctionString += n.nodeid+"d"+"\t"+n.elevation+"\t"+n.demand+"\tSecPat"+"\n";
						esrNodes.add(n.nodeid);
					}
					continue;
				}
				String secPat = "";
				if(n.nodeid!=n.esr)
					secPat = "\tSecPat";
				junctionString += n.nodeid+"\t"+n.elevation+"\t"+n.demand+secPat+"\n";
			}
			
			
			
			Set<Integer> pipesSeen = new HashSet<Integer>();
			Set<Integer> seriesPipes = new HashSet<Integer>();
			HashMap<Integer,Double> seriesTotalLength = new HashMap<Integer,Double>();
			for(PipeStruct p : resultPipesArray){
				if(!pipesSeen.add(p.pipeid)){
					if(!p.parallelallowed){
						seriesPipes.add(p.pipeid);
						seriesTotalLength.put(p.pipeid, p.length+seriesTotalLength.get(p.pipeid));
					}
				}
				else{
					seriesTotalLength.put(p.pipeid, p.length);
				}
			}
			pipesSeen = new HashSet<Integer>();
			for(PipeStruct p : resultPipesArray){
				
				String startnode = p.startnode+"";
				String endnode = p.endnode+"";
				String pipeid = p.pipeid+"";
				
				if(esrs.contains(p.startnode)){
					if(p.isprimary)
						startnode += "j";
					else
						startnode += "t";
				}
				if(esrs.contains(p.endnode)){
					if(esrsWithPrimaryChildren.contains(p.endnode))
						endnode += "j";
					else
						endnode += "t";
				}
				
				
				if(pipesSeen.add(p.pipeid)){
					if(seriesPipes.contains(p.pipeid)){
						endnode = startnode+"s"+endnode;
						double elev = getLinearElevation(p.startnode, p.endnode, p.length, seriesTotalLength.get(p.pipeid), resultNodesArray);
						junctionString += endnode+"\t"+elev+"\t"+"0.0"+"\n";
						
						//String[] startcoord = coord.get(p.startnode).split(" ");
						//String[] endcoord = coord.get(p.endnode).split(" ");
						
						//coordinateString += endnode + " " + 
						//(Double.parseDouble(startcoord[0])+Double.parseDouble(endcoord[0]))/2 + " " +
						//(Double.parseDouble(startcoord[1])+Double.parseDouble(endcoord[1]))/2 + "\n";
					}
					if(esrsWithPrimaryChildren.contains(p.endnode)){
						pipeString += p.endnode+"j"+"\t"+p.endnode+"j"+"\t"+p.endnode+"t"+"\t"+
							      10+"\t"+p.diameter+"\t"+p.roughness+"\t"+
							      "0\tOpen\n";
					}
					if(esrNodes.contains(p.endnode)){
						pipeString += p.endnode+"d"+"\t"+p.endnode+"t"+"\t"+p.endnode+"d"+"\t"+
							      10+"\t"+p.diameter+"\t"+p.roughness+"\t"+
							      "0\tOpen\n";
					}
					
					if(pumpenabled && pumppipes.keySet().contains(p.pipeid)){
						String tempnode = startnode + "pump";
						double elev = getLinearElevation(p.startnode, p.startnode, 0, 0, resultNodesArray);
						junctionString += tempnode+"\t"+elev+"\t"+"0.0"+"\n";
						pumpString += p.pipeid+"pump\t"+startnode+"\t"+tempnode+"\t"+"POWER "+pumppipes.get(p.pipeid).pumppower*pumpGeneralProperties.efficiency/100
								+"\tPATTERN "+(esrenabled?(p.isprimary?"PriPat":"SecPat"):"")+"\n";
						startnode = tempnode;
					}
					if(valvepipes.keySet().contains(p.pipeid)){
						String tempnode = startnode + "valve";
						double elev = getLinearElevation(p.startnode, p.startnode, 0, 0, resultNodesArray);
						junctionString += tempnode+"\t"+elev+"\t"+"0.0"+"\n";
						valveString += p.pipeid+"valve\t"+startnode+"\t"+tempnode+"\t"+p.diameter+"\tPBV\t"+valvepipes.get(p.pipeid).valvesetting+"\n";
						startnode = tempnode;
					}
				}
				else{
				//pipeid already seen => parallel or series pipes
					if(p.parallelallowed){
						// add to vertices here
						pipeid += "p";
						if(pumpenabled && pumppipes.keySet().contains(p.pipeid)){
							startnode += "pump";
						}
						if(valvepipes.keySet().contains(p.pipeid)){
							startnode += "valve";
						}
						//String[] startcoord = coord.get(p.startnode).split(" ");
						//String[] endcoord = coord.get(p.endnode).split(" ");
						
						//verticesString += pipeid + " " + 
						//		((Double.parseDouble(startcoord[0])+Double.parseDouble(endcoord[0]))/2 - 150) + " " +
						//		(Double.parseDouble(startcoord[1])+Double.parseDouble(endcoord[1]))/2 + "\n";
					}
					else{
						startnode = startnode+"s"+endnode;
						pipeid += "s";
					}
				}
				
				pipeString += pipeid+"\t"+startnode+"\t"+endnode+"\t"+
					      p.length+"\t"+p.diameter+"\t"+p.roughness+"\t"+
					      "0\tOpen\n";
			}		
			
			//coordinateString += generateCoordinates(resultPipesArray, resultNodesArray, source, 0, 0, -10000, 10000);
			//coordinateString += resultCoordinatesString;
		
			pw.println(titleString);
			pw.println(junctionString);
			if(esrenabled){
				pw.println(tanksString);
				pw.println(patternString);
				pw.println(timesString);
			}
			pw.println(reservoirString);
			if(pumpenabled){
				pw.println(pumpString);
			}
			pw.println(valveString);
			pw.println(pipeString);
			pw.println(optionString);
			pw.println(coordinateString);
			pw.println(verticesString);
			pw.println(endString);
			
			pw.flush();
			pw.close();
			
			os.flush();
			os.close();
			
			
    	}
    	catch(Exception e){
    		System.out.println(e);
    	}
    }
    
    // get elevation of a point between startnode and endnode
    // point is 'length' distance from startnode
    // total distance between startnode and endnode is 'totallength'
    protected double getLinearElevation(int startnode, int endnode,
			double length, double totallength, NodeStruct[] nodesArray) {
		
    	double startElevation=0,endElevation =0;
    	for(NodeStruct node: nodesArray){
    		if(node.nodeid==startnode){
    			startElevation = node.elevation;
    		}
    		if(node.nodeid==endnode){
    			endElevation = node.elevation;
    		}
    	}
    	
    	if(totallength==0)
    		return (startElevation+endElevation)/2;
    	else
    		return startElevation + (length/totallength)*(endElevation-startElevation);
	}

	/*protected List<PipeStruct> getOutgoingPipes(NodeStruct n, PipeStruct[] pipes){
    	List<PipeStruct> outgoingPipes = new ArrayList<PipeStruct>();
    	for(PipeStruct pipe: pipes){
    		if(n.nodeid==pipe.startnode)
    			outgoingPipes.add(pipe);
    	}
    	return outgoingPipes;
    }*/
    
    /*protected String generateCoordinates(PipeStruct[] pipes, NodeStruct[] nodes, NodeStruct head, double x, double y, double minx, double maxx){
		String coordinateString = head.nodeid+" "+x+" "+y+"\n";
		
		List<PipeStruct> outgoingPipes = getOutgoingPipes(head, pipes);
		int n = outgoingPipes.size();
		
		if(n>1){
			double min_length = Double.MAX_VALUE;
			for(PipeStruct p : outgoingPipes){
				min_length = Math.min(min_length, p.length);
			}
			double max_width = min_length/(1-(1/(double)n));
			minx = Math.max(minx, x-max_width);
			maxx = Math.min(maxx, x+max_width);
		}
		for(int i=0;i<n;i++){
			PipeStruct p = outgoingPipes.get(i);
			double newminx = minx + (maxx-minx)*i/n;
			double newmaxx = minx + (maxx-minx)*(i+1)/n;
			double newx = (newminx+newmaxx)/2;
			double newy = y + Math.sqrt(p.length*p.length - (newx-x)*(newx-x));
			coordinateString += generateCoordinates(pipes, nodes, getEndNode(p,nodes), newx, newy, newminx, newmaxx);
		}
		return coordinateString;
	}*/
    
    /*protected NodeStruct getEndNode(PipeStruct pipe, NodeStruct[] nodes){
    	for(NodeStruct n:nodes){
    		if(pipe.endnode==n.nodeid)
    			return n;
    	}
    	return null;
    }*/
    
    //generate and upload Excel output network file
    protected void uploadExcelOutputFile(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
    	try
    	{
    		OutputStream os = response.getOutputStream();
    		
			Gson gson = new Gson(); 
	
			GeneralStruct generalProperties = gson.fromJson(request.getParameter("general"), GeneralStruct.class);
			NodeStruct[] resultNodesArray = gson.fromJson(request.getParameter("resultnodes"), NodeStruct[].class);
			PipeStruct[] resultPipesArray = gson.fromJson(request.getParameter("resultpipes"), PipeStruct[].class);
			CommercialPipeStruct[] resultCostsArray = gson.fromJson(request.getParameter("resultcosts"), CommercialPipeStruct[].class);
			EsrGeneralStruct esrGeneralProperties = gson.fromJson(request.getParameter("esrGeneral"), EsrGeneralStruct.class);
			ResultEsrStruct[] resultEsrsArray = gson.fromJson(request.getParameter("resultesrs"), ResultEsrStruct[].class);
			PumpGeneralStruct pumpGeneralProperties = gson.fromJson(request.getParameter("pumpGeneral"), PumpGeneralStruct.class);
			ResultPumpStruct[] resultPumpsArray = gson.fromJson(request.getParameter("resultpumps"), ResultPumpStruct[].class);
			//ValveStruct[] valves = gson.fromJson(request.getParameter("valves"), ValveStruct[].class);
			
			if(generalProperties.name_project==null || generalProperties.name_project.isEmpty())
				generalProperties.name_project="JalTantra Project";
			
			String filename = generalProperties.name_project+"_output.xls";
			
			response.setContentType("application/vnd.ms-excel"); // Set up mime type
			response.setHeader("Content-Disposition", "attachment; filename="+filename);
	
			Workbook wb = new HSSFWorkbook();
    	
	    	Font font = wb.createFont();
	    	font.setBold(true);
	    	CellStyle cellStyle = wb.createCellStyle();
	    	cellStyle.setFont(font);
	    	cellStyle.setAlignment(CellStyle.ALIGN_CENTER);
	    	cellStyle.setVerticalAlignment(CellStyle.VERTICAL_CENTER);
	    	cellStyle.setBorderBottom(CellStyle.BORDER_THIN);
	    	cellStyle.setBorderRight(CellStyle.BORDER_THIN);
	    	cellStyle.setBorderLeft(CellStyle.BORDER_THIN);
	    	cellStyle.setBorderTop(CellStyle.BORDER_THIN);
	    	cellStyle.setWrapText(true);
	    	
	    	CellStyle redColorCellStyle = wb.createCellStyle();
	    	redColorCellStyle.setFont(font);
	    	redColorCellStyle.setAlignment(CellStyle.ALIGN_CENTER);
	    	redColorCellStyle.setVerticalAlignment(CellStyle.VERTICAL_CENTER);
	    	redColorCellStyle.setBorderBottom(CellStyle.BORDER_THIN);
	    	redColorCellStyle.setBorderRight(CellStyle.BORDER_THIN);
	    	redColorCellStyle.setBorderLeft(CellStyle.BORDER_THIN);
	    	redColorCellStyle.setBorderTop(CellStyle.BORDER_THIN);
	    	redColorCellStyle.setWrapText(true);
	    	redColorCellStyle.setFillBackgroundColor(IndexedColors.RED.getIndex());
	    	
	    	CellStyle largeBoldCellStyle = wb.createCellStyle();
	    	largeBoldCellStyle.setAlignment(CellStyle.ALIGN_CENTER);
	    	largeBoldCellStyle.setVerticalAlignment(CellStyle.VERTICAL_CENTER);
	    	font = wb.createFont();
	    	font.setBold(true);
	    	font.setFontHeightInPoints((short) 16);
	    	largeBoldCellStyle.setFont(font);
	    	
	    	CellStyle smallBoldCellStyle = wb.createCellStyle();
	    	smallBoldCellStyle.setAlignment(CellStyle.ALIGN_CENTER);
	    	smallBoldCellStyle.setVerticalAlignment(CellStyle.VERTICAL_CENTER);
	    	font = wb.createFont();
	    	font.setBold(true);
	    	font.setFontHeightInPoints((short) 12);
	    	smallBoldCellStyle.setFont(font);
	    	
	    	CellStyle integerStyle = wb.createCellStyle();
			DataFormat integerFormat = wb.createDataFormat();
			integerStyle.setDataFormat(integerFormat.getFormat("#,##0"));
			integerStyle.setAlignment(CellStyle.ALIGN_CENTER);
			integerStyle.setVerticalAlignment(CellStyle.VERTICAL_CENTER);
			integerStyle.setBorderBottom(CellStyle.BORDER_THIN);
			integerStyle.setBorderRight(CellStyle.BORDER_THIN);
			integerStyle.setBorderLeft(CellStyle.BORDER_THIN);
			integerStyle.setBorderTop(CellStyle.BORDER_THIN);
			
			CellStyle doubleStyle = wb.createCellStyle();
			DataFormat doubleFormat = wb.createDataFormat();
			doubleStyle.setDataFormat(doubleFormat.getFormat("#,##0.00"));
			doubleStyle.setAlignment(CellStyle.ALIGN_CENTER);
			doubleStyle.setVerticalAlignment(CellStyle.VERTICAL_CENTER);
			doubleStyle.setBorderBottom(CellStyle.BORDER_THIN);
			doubleStyle.setBorderRight(CellStyle.BORDER_THIN);
			doubleStyle.setBorderLeft(CellStyle.BORDER_THIN);
			doubleStyle.setBorderTop(CellStyle.BORDER_THIN);
			
			CellStyle doubleStyle2 = wb.createCellStyle();
			DataFormat doubleFormat2 = wb.createDataFormat();
			doubleStyle2.setDataFormat(doubleFormat2.getFormat("#,##0.000"));
			doubleStyle2.setAlignment(CellStyle.ALIGN_CENTER);
			doubleStyle2.setVerticalAlignment(CellStyle.VERTICAL_CENTER);
			doubleStyle2.setBorderBottom(CellStyle.BORDER_THIN);
			doubleStyle2.setBorderRight(CellStyle.BORDER_THIN);
			doubleStyle2.setBorderLeft(CellStyle.BORDER_THIN);
			doubleStyle2.setBorderTop(CellStyle.BORDER_THIN);
	    	
	    	Sheet generalSheet = wb.createSheet("General");
	    	
	    	PrintSetup ps = generalSheet.getPrintSetup();
	
	    	ps.setScale((short)80);
	
	    	int rowindex = 0;
	    	int noderowindex , piperowindex, costrowindex, esrcostrowindex, pumpcostrowindex;
	    	int generalstart, nodestart , pipestart, coststart, esrcoststart, pumpcoststart;
	    	
	    	Row row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,10));
	    	rowindex++;
	    	Cell generalCell = row.createCell(0);
	    	generalCell.setCellValue("JalTantra: System For Optimization of Piped Water Networks, version:"+version);
	    	generalCell.setCellStyle(largeBoldCellStyle);
	    	row.setHeightInPoints((float) 21.75);
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,10));
	    	rowindex++;
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue("developed by CSE and CTARA departments of IIT Bombay");
	    	generalCell.setCellStyle(largeBoldCellStyle);
	    	row.setHeightInPoints((float) 21.75);
	    	
	    	
	    	Date date = new Date();
	    	DateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm a");
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,10));
	    	rowindex++;
	    	
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue(dateFormat.format(date));
	    	generalCell.setCellStyle(smallBoldCellStyle);
	    	
	    	rowindex = rowindex + 2;
	        if(rowindex%2!=0)
	        	rowindex++;
	    	generalstart = rowindex;
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,3,5));
	    	rowindex++;
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue("Network Name");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	generalCell.setCellValue(generalProperties.name_project);
	    	CellStyle tempCellStyle = wb.createCellStyle();
	    	tempCellStyle.cloneStyleFrom(cellStyle);
	    	font = wb.createFont();
	    	font.setBold(true);
	    	font.setFontHeightInPoints((short) 12);
	    	tempCellStyle.setFont(font);
	    	generalCell.setCellStyle(tempCellStyle);
	    	generalCell = row.createCell(4);
	    	generalCell.setCellStyle(tempCellStyle);
	    	generalCell = row.createCell(5);
	    	generalCell.setCellStyle(tempCellStyle);
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	rowindex++;
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue("Organization Name");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	generalCell.setCellValue(generalProperties.name_organization);
	    	generalCell.setCellStyle(integerStyle);
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	rowindex++;
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue("Minimum Node Pressure");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	generalCell.setCellValue(generalProperties.min_node_pressure);
	    	generalCell.setCellStyle(integerStyle);
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	rowindex++;
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue("Default Pipe Roughness 'C'");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	generalCell.setCellValue(generalProperties.def_pipe_roughness);
	    	generalCell.setCellStyle(integerStyle);
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	rowindex++;
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue("Minimum Headloss per KM");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	generalCell.setCellValue(generalProperties.min_hl_perkm);
	    	generalCell.setCellStyle(doubleStyle2);
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	rowindex++;
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue("Maximum Headloss per KM");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	generalCell.setCellValue(generalProperties.max_hl_perkm);
	    	generalCell.setCellStyle(doubleStyle2);
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	rowindex++;
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue("Maximum Water Speed");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	if(generalProperties.max_water_speed>0)
	    		generalCell.setCellValue(generalProperties.max_water_speed);
	    	generalCell.setCellStyle(doubleStyle2);
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	rowindex++;
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue("Maximum Pipe Pressure");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	if(generalProperties.max_pipe_pressure>0)
	    		generalCell.setCellValue(generalProperties.max_pipe_pressure);
	    	generalCell.setCellStyle(doubleStyle2);
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	rowindex++;
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue("Number of Supply Hours");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	generalCell.setCellValue(generalProperties.supply_hours);
	    	generalCell.setCellStyle(integerStyle);
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	rowindex++;
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue("Source Node ID");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	generalCell.setCellValue(generalProperties.source_nodeid);
	    	generalCell.setCellStyle(integerStyle);
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	rowindex++;
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue("Source Node Name");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	generalCell.setCellValue(generalProperties.source_nodename);
	    	generalCell.setCellStyle(integerStyle);
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	rowindex++;
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue("Source Elevation");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	generalCell.setCellValue(generalProperties.source_elevation);
	    	generalCell.setCellStyle(doubleStyle);
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	rowindex++;
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue("Source Head");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	generalCell.setCellValue(generalProperties.source_head);
	    	generalCell.setCellStyle(doubleStyle);
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	rowindex++;
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue("Number of Nodes");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	generalCell.setCellValue(resultNodesArray.length);
	    	generalCell.setCellStyle(integerStyle);
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	rowindex++;
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue("ESR Enabled");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	generalCell.setCellValue(esrGeneralProperties.esr_enabled);
	    	generalCell.setCellStyle(integerStyle);
	    	
	    	if(esrGeneralProperties.esr_enabled){
	    				    	
		    	row = generalSheet.createRow(rowindex);
		    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
		    	rowindex++;
		    	generalCell = row.createCell(0);
		    	generalCell.setCellValue("Secondary Supply Hours");
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(1);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(2);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(3);
		    	generalCell.setCellValue(esrGeneralProperties.secondary_supply_hours);
		    	generalCell.setCellStyle(integerStyle);
		    	
		    	row = generalSheet.createRow(rowindex);
		    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
		    	rowindex++;
		    	generalCell = row.createCell(0);
		    	generalCell.setCellValue("ESR Capacity Factor");
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(1);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(2);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(3);
		    	generalCell.setCellValue(esrGeneralProperties.esr_capacity_factor);
		    	generalCell.setCellStyle(doubleStyle);
			    
		    	row = generalSheet.createRow(rowindex);
		    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
		    	rowindex++;
		    	generalCell = row.createCell(0);
		    	generalCell.setCellValue("Maximum ESR Height");
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(1);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(2);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(3);
		    	generalCell.setCellValue(esrGeneralProperties.max_esr_height);
		    	generalCell.setCellStyle(integerStyle);
			    
		    	row = generalSheet.createRow(rowindex);
		    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
		    	rowindex++;
		    	generalCell = row.createCell(0);
		    	generalCell.setCellValue("Allow ESRs at zero demand nodes");
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(1);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(2);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(3);
		    	generalCell.setCellValue(esrGeneralProperties.allow_dummy);
		    	generalCell.setCellStyle(integerStyle);
		    	
			    String mustHaveEsrs = "";
			    if(esrGeneralProperties.must_esr!=null){
				    for (int nodeid : esrGeneralProperties.must_esr){
				    	mustHaveEsrs = mustHaveEsrs + nodeid + ";"; 
				    }
				    if(mustHaveEsrs.length()>0){
				    	mustHaveEsrs = mustHaveEsrs.substring(0, mustHaveEsrs.length()-1);
				    }
			    }
			    
			    String mustNotHaveEsrs = "";
			    if(esrGeneralProperties.must_not_esr!=null){
				    for (int nodeid : esrGeneralProperties.must_not_esr){
				    	mustNotHaveEsrs = mustNotHaveEsrs + nodeid + ";"; 
				    }
				    if(mustNotHaveEsrs.length()>0){
				    	mustNotHaveEsrs = mustNotHaveEsrs.substring(0, mustNotHaveEsrs.length()-1);
				    }
			    }
			    
			    row = generalSheet.createRow(rowindex);
		    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
		    	rowindex++;
		    	generalCell = row.createCell(0);
		    	generalCell.setCellValue("Nodes that must have ESRs");
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(1);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(2);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(3);
		    	generalCell.setCellValue(mustHaveEsrs);
		    	generalCell.setCellStyle(integerStyle);
		    	
		    	row = generalSheet.createRow(rowindex);
		    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
		    	rowindex++;
		    	generalCell = row.createCell(0);
		    	generalCell.setCellValue("Nodes that must not have ESRs");
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(1);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(2);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(3);
		    	generalCell.setCellValue(mustNotHaveEsrs);
		    	generalCell.setCellStyle(integerStyle);		    			    
	    	}
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	rowindex++;
	    	generalCell = row.createCell(0);
	    	generalCell.setCellValue("Pump Enabled");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	generalCell.setCellValue(pumpGeneralProperties.pump_enabled);
	    	generalCell.setCellStyle(integerStyle);
	    	
	    	if(pumpGeneralProperties.pump_enabled){
		    	row = generalSheet.createRow(rowindex);
		    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
		    	rowindex++;
		    	generalCell = row.createCell(0);
		    	generalCell.setCellValue("Minimum Pump Size");
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(1);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(2);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(3);
		    	generalCell.setCellValue(pumpGeneralProperties.minpumpsize);
		    	generalCell.setCellStyle(doubleStyle);
		    	
		    	row = generalSheet.createRow(rowindex);
		    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
		    	rowindex++;
		    	generalCell = row.createCell(0);
		    	generalCell.setCellValue("Pump Efficiency");
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(1);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(2);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(3);
		    	generalCell.setCellValue(pumpGeneralProperties.efficiency);
		    	generalCell.setCellStyle(integerStyle);
			    
		    	row = generalSheet.createRow(rowindex);
		    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
		    	rowindex++;
		    	generalCell = row.createCell(0);
		    	generalCell.setCellValue("Capital Cost per kW");
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(1);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(2);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(3);
		    	generalCell.setCellValue(pumpGeneralProperties.capitalcost_per_kw);
		    	generalCell.setCellStyle(integerStyle);
			    
		    	row = generalSheet.createRow(rowindex);
		    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
		    	rowindex++;
		    	generalCell = row.createCell(0);
		    	generalCell.setCellValue("Energy Cost per kWh");
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(1);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(2);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(3);
		    	generalCell.setCellValue(pumpGeneralProperties.energycost_per_kwh);
		    	generalCell.setCellStyle(doubleStyle);
		    	
		    	row = generalSheet.createRow(rowindex);
		    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
		    	rowindex++;
		    	generalCell = row.createCell(0);
		    	generalCell.setCellValue("Design Lifetime");
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(1);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(2);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(3);
		    	generalCell.setCellValue(pumpGeneralProperties.design_lifetime);
		    	generalCell.setCellStyle(integerStyle);
		    	
		    	row = generalSheet.createRow(rowindex);
		    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
		    	rowindex++;
		    	generalCell = row.createCell(0);
		    	generalCell.setCellValue("Discount Rate");
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(1);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(2);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(3);
		    	generalCell.setCellValue(pumpGeneralProperties.discount_rate);
		    	generalCell.setCellStyle(doubleStyle);
		    	
		    	row = generalSheet.createRow(rowindex);
		    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
		    	rowindex++;
		    	generalCell = row.createCell(0);
		    	generalCell.setCellValue("Inflation Rate");
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(1);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(2);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(3);
		    	generalCell.setCellValue(pumpGeneralProperties.inflation_rate);
		    	generalCell.setCellStyle(doubleStyle);
		    				    
			    String mustNotHavePumps = "";
			    if(pumpGeneralProperties.must_not_pump!=null){
				    for (int pipeid : pumpGeneralProperties.must_not_pump){
				    	mustNotHavePumps = mustNotHavePumps + pipeid + ";"; 
				    }
				    if(mustNotHavePumps.length()>0){
				    	mustNotHavePumps = mustNotHavePumps.substring(0, mustNotHavePumps.length()-1);
				    }
			    }
			    
		    	row = generalSheet.createRow(rowindex);
		    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
		    	rowindex++;
		    	generalCell = row.createCell(0);
		    	generalCell.setCellValue("Pipes that must not have Pumps");
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(1);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(2);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(3);
		    	generalCell.setCellValue(mustNotHavePumps);
		    	generalCell.setCellStyle(integerStyle);		    			    
	    	}
	    	
	    	Sheet nodeSheet = generalSheet;//wb.createSheet("Nodes");
	        noderowindex = rowindex + 10;
	        if(noderowindex%2==0)
	        	noderowindex++;
	        
	        row = nodeSheet.createRow(noderowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(noderowindex,noderowindex,0,6));
	        noderowindex++;
	        Cell nodeCell = row.createCell(0);
	        nodeCell.setCellValue("NODE RESULTS");
	        nodeCell.setCellStyle(cellStyle);
	    	for(int i=1;i<7;i++)
	    	{
	    		nodeCell = row.createCell(i);
	    		nodeCell.setCellStyle(cellStyle);
	    	}
	    	nodestart = noderowindex;
	        Row nodeTitleRow = nodeSheet.createRow(noderowindex);
	        noderowindex++;
	        nodeCell = nodeTitleRow.createCell(0);
	        nodeCell.setCellValue("Node ID");
	        nodeCell.setCellStyle(cellStyle);
	        nodeCell = nodeTitleRow.createCell(1);
	        nodeCell.setCellValue("Node Name");
	        nodeCell.setCellStyle(cellStyle);
	        nodeCell = nodeTitleRow.createCell(2);
	        nodeCell.setCellValue("Demand");
	        nodeCell.setCellStyle(cellStyle);
	        nodeCell = nodeTitleRow.createCell(3);
	        nodeCell.setCellValue("Elevation");
	        nodeCell.setCellStyle(cellStyle);
	        nodeCell = nodeTitleRow.createCell(4);
	        nodeCell.setCellValue("Head");
	        nodeCell.setCellStyle(cellStyle);
	        nodeCell = nodeTitleRow.createCell(5);
	        nodeCell.setCellValue("Pressure");
	        nodeCell.setCellStyle(cellStyle);
	        nodeCell = nodeTitleRow.createCell(6);
	        nodeCell.setCellValue("Min. Pressure");
	        nodeCell.setCellStyle(cellStyle);
	        
	        for(NodeStruct node : resultNodesArray)
			{
	        	if(node.nodeid==generalProperties.source_nodeid)
	        		continue;
	        	
				Row nodeRow = nodeSheet.createRow(noderowindex);
		        noderowindex++;
				
				nodeCell = nodeRow.createCell(0);
				nodeCell.setCellValue(node.nodeid);
				nodeCell.setCellStyle(integerStyle);
				
				nodeCell = nodeRow.createCell(1);
				nodeCell.setCellValue(node.nodename);
				nodeCell.setCellStyle(integerStyle);
				
				nodeCell = nodeRow.createCell(2);
				nodeCell.setCellValue(node.demand);
				nodeCell.setCellStyle(doubleStyle2);
				
				nodeCell = nodeRow.createCell(3);
				nodeCell.setCellValue(node.elevation);
				nodeCell.setCellStyle(doubleStyle);
				
				nodeCell = nodeRow.createCell(4);
				nodeCell.setCellValue(node.head);
				nodeCell.setCellStyle(doubleStyle);
				
				nodeCell = nodeRow.createCell(5);
				nodeCell.setCellValue(node.pressure);
				nodeCell.setCellStyle(doubleStyle);
				
				nodeCell = nodeRow.createCell(6);
				nodeCell.setCellValue(node.minpressure);
				nodeCell.setCellStyle(integerStyle);
			}
	        
	        Sheet pipeSheet = generalSheet;// wb.createSheet("Pipes");
	        
	        piperowindex = noderowindex + 2;
	        if(piperowindex%2==0)
	        	piperowindex++;
	        row = generalSheet.createRow(piperowindex);
	        generalSheet.addMergedRegion(new CellRangeAddress(piperowindex,piperowindex,0,14));
	        piperowindex++;
	        Cell pipeCell = row.createCell(0);
	        pipeCell.setCellValue("PIPE RESULTS");
	        pipeCell.setCellStyle(cellStyle);
	    	for(int i=1;i<15;i++)
	    	{
	    		pipeCell = row.createCell(i);
	    		pipeCell.setCellStyle(cellStyle);
	    	}
	        pipestart = piperowindex;
	        Row pipeTitleRow = pipeSheet.createRow(piperowindex);
	        piperowindex++;
	        pipeCell = pipeTitleRow.createCell(0);
	        pipeCell.setCellValue("Pipe ID");
	        pipeCell.setCellStyle(cellStyle);
	        pipeCell = pipeTitleRow.createCell(1);
	        pipeCell.setCellValue("Start Node");
	        pipeCell.setCellStyle(cellStyle);
	        pipeCell = pipeTitleRow.createCell(2);
	        pipeCell.setCellValue("End Node");
	        pipeCell.setCellStyle(cellStyle);
	        pipeCell = pipeTitleRow.createCell(3);
	        pipeCell.setCellValue("Length");
	        pipeCell.setCellStyle(cellStyle);
	        pipeCell = pipeTitleRow.createCell(4);
	        pipeCell.setCellValue("Flow");
	        pipeCell.setCellStyle(cellStyle);
	        pipeCell = pipeTitleRow.createCell(5);
	        pipeCell.setCellValue("Speed");
	        pipeCell.setCellStyle(cellStyle);
	        pipeCell = pipeTitleRow.createCell(6);
	        pipeCell.setCellValue("Diameter");
	        pipeCell.setCellStyle(cellStyle);
	        pipeCell = pipeTitleRow.createCell(7);
	        pipeCell.setCellValue("Roughness 'C'");
	        pipeCell.setCellStyle(cellStyle);
	        pipeCell = pipeTitleRow.createCell(8);
	        pipeCell.setCellValue("Headloss");
	        pipeCell.setCellStyle(cellStyle);
	        pipeCell = pipeTitleRow.createCell(9);
	        pipeCell.setCellValue("Headloss per KM");
	        pipeCell.setCellStyle(cellStyle);
	        pipeCell = pipeTitleRow.createCell(10);
	        pipeCell.setCellValue("Cost");
	        pipeCell.setCellStyle(cellStyle);
	        pipeCell = pipeTitleRow.createCell(11);
	        pipeCell.setCellValue("Pump Head");
	        pipeCell.setCellStyle(cellStyle);
	        pipeCell = pipeTitleRow.createCell(12);
	        pipeCell.setCellValue("Pump Power");
	        pipeCell.setCellStyle(cellStyle);
	        pipeCell = pipeTitleRow.createCell(13);
	        pipeCell.setCellValue("Valve Setting");
	        pipeCell.setCellStyle(cellStyle);
	        pipeCell = pipeTitleRow.createCell(14);
	        pipeCell.setCellValue("Status");
	        pipeCell.setCellStyle(cellStyle);
	        
	        Font tempFont = wb.createFont();
        	tempFont.setColor(IndexedColors.RED.getIndex());
        	
        	CellStyle tempIntegerStyle = wb.createCellStyle();
        	tempIntegerStyle.setDataFormat(integerFormat.getFormat("#,##0"));
        	tempIntegerStyle.setAlignment(CellStyle.ALIGN_CENTER);
        	tempIntegerStyle.setVerticalAlignment(CellStyle.VERTICAL_CENTER);
        	tempIntegerStyle.setBorderBottom(CellStyle.BORDER_THIN);
        	tempIntegerStyle.setBorderRight(CellStyle.BORDER_THIN);
        	tempIntegerStyle.setBorderLeft(CellStyle.BORDER_THIN);
        	tempIntegerStyle.setBorderTop(CellStyle.BORDER_THIN);
        	tempIntegerStyle.setFont(tempFont);
			
			CellStyle tempDoubleStyle = wb.createCellStyle();
			tempDoubleStyle.setDataFormat(doubleFormat.getFormat("#,##0.00"));
			tempDoubleStyle.setAlignment(CellStyle.ALIGN_CENTER);
			tempDoubleStyle.setVerticalAlignment(CellStyle.VERTICAL_CENTER);
			tempDoubleStyle.setBorderBottom(CellStyle.BORDER_THIN);
			tempDoubleStyle.setBorderRight(CellStyle.BORDER_THIN);
			tempDoubleStyle.setBorderLeft(CellStyle.BORDER_THIN);
			tempDoubleStyle.setBorderTop(CellStyle.BORDER_THIN);
			tempDoubleStyle.setFont(tempFont);
			
			CellStyle tempDoubleStyle2 = wb.createCellStyle();
			tempDoubleStyle2.setDataFormat(doubleFormat2.getFormat("#,##0.000"));
			tempDoubleStyle2.setAlignment(CellStyle.ALIGN_CENTER);
			tempDoubleStyle2.setVerticalAlignment(CellStyle.VERTICAL_CENTER);
			tempDoubleStyle2.setBorderBottom(CellStyle.BORDER_THIN);
			tempDoubleStyle2.setBorderRight(CellStyle.BORDER_THIN);
			tempDoubleStyle2.setBorderLeft(CellStyle.BORDER_THIN);
			tempDoubleStyle2.setBorderTop(CellStyle.BORDER_THIN);
			tempDoubleStyle2.setFont(tempFont);
			
			CellStyle useIntegerStyle, useDoubleStyle, useDoubleStyle2;
			
	        double cumulativeOldLength = 0;
	        for(PipeStruct pipe : resultPipesArray)
			{
				Row pipeRow = pipeSheet.createRow(piperowindex);
		        piperowindex++;
		    	        		         
		        if(pipe.pressureexceeded){					 
		        	useIntegerStyle = tempIntegerStyle;
		        	useDoubleStyle = tempDoubleStyle;
		        	useDoubleStyle2 = tempDoubleStyle2;
				}
		        else{
		        	useIntegerStyle = integerStyle;
		        	useDoubleStyle = doubleStyle;
		        	useDoubleStyle2 = doubleStyle2;
		        }
		        
		        if(!pipe.parallelallowed)
		        {
		        	pipeCell = pipeRow.createCell(0);
					pipeCell.setCellValue(pipe.pipeid);
					pipeCell.setCellStyle(useIntegerStyle);
					
					pipeCell = pipeRow.createCell(1);
					pipeCell.setCellValue(pipe.startnode);
					pipeCell.setCellStyle(useIntegerStyle);
					
					pipeCell = pipeRow.createCell(2);
					pipeCell.setCellValue(pipe.endnode);
					pipeCell.setCellStyle(useIntegerStyle);
		        }
				else
				{
					pipeCell = pipeRow.createCell(0);
					pipeCell.setCellStyle(useIntegerStyle);
					
					pipeCell = pipeRow.createCell(1);
					pipeCell.setCellStyle(useIntegerStyle);
					
					pipeCell = pipeRow.createCell(2);
					pipeCell.setCellStyle(useIntegerStyle);
				}
		        
		        pipeCell = pipeRow.createCell(3);
				pipeCell.setCellValue(pipe.length);
				pipeCell.setCellStyle(useIntegerStyle);
				
				pipeCell = pipeRow.createCell(4);
				pipeCell.setCellValue(pipe.flow);
				pipeCell.setCellStyle(useDoubleStyle);
				
				pipeCell = pipeRow.createCell(5);
				pipeCell.setCellValue(pipe.speed);
				pipeCell.setCellStyle(useDoubleStyle2);
				
				pipeCell = pipeRow.createCell(6);
				pipeCell.setCellValue(pipe.diameter);
				pipeCell.setCellStyle(useIntegerStyle);
				
				pipeCell = pipeRow.createCell(7);
				pipeCell.setCellValue(pipe.roughness);
				pipeCell.setCellStyle(useIntegerStyle);
				
				pipeCell = pipeRow.createCell(8);
				pipeCell.setCellValue(pipe.headloss);
				pipeCell.setCellStyle(useDoubleStyle2);
				
				pipeCell = pipeRow.createCell(9);
				pipeCell.setCellValue(pipe.headlossperkm);
				pipeCell.setCellStyle(useDoubleStyle2);
											
				pipeCell = pipeRow.createCell(10);
				pipeCell.setCellValue(pipe.cost);
				pipeCell.setCellStyle(useIntegerStyle);
				
				pipeCell = pipeRow.createCell(11);
				if(pipe.pumphead>0)
					pipeCell.setCellValue(pipe.pumphead);
				pipeCell.setCellStyle(useDoubleStyle);
				
				pipeCell = pipeRow.createCell(12);
				if(pipe.pumppower>0)
					pipeCell.setCellValue(pipe.pumppower);
				pipeCell.setCellStyle(useDoubleStyle);
				
				pipeCell = pipeRow.createCell(13);
				if(pipe.valvesetting>0)
					pipeCell.setCellValue(pipe.valvesetting);
				pipeCell.setCellStyle(useDoubleStyle);
				
				pipeCell = pipeRow.createCell(14);
				pipeCell.setCellStyle(useIntegerStyle);
				if(pipe.parallelallowed)
				{
					pipeCell.setCellValue("Parallel");
					cumulativeOldLength += pipe.length;
				}
			}
			
		    Sheet costSheet = generalSheet; //wb.createSheet("Cost");
		    
		    costrowindex = piperowindex + 2;
		    if(costrowindex%2==0)
		    	costrowindex++;
		    row = generalSheet.createRow(costrowindex);
		    generalSheet.addMergedRegion(new CellRangeAddress(costrowindex,costrowindex,0,3));
		    costrowindex++;
	        Cell costCell = row.createCell(0);
	        costCell.setCellValue("COST RESULTS OF NEW PIPES");
	        costCell.setCellStyle(cellStyle);
	    	for(int i=1;i<4;i++)
	    	{
	    		costCell = row.createCell(i);
	    		costCell.setCellStyle(cellStyle);
	    	}
		    coststart = costrowindex;
	    	Row costTitleRow = costSheet.createRow(costrowindex);
	    	costrowindex++;
	    	costCell = costTitleRow.createCell(0);
	    	costCell.setCellValue("Diameter");
	    	costCell.setCellStyle(cellStyle);
	    	costCell = costTitleRow.createCell(1);
	    	costCell.setCellValue("Length");
	    	costCell.setCellStyle(cellStyle);
	    	costCell = costTitleRow.createCell(2);
	    	costCell.setCellValue("Cost");
	    	costCell.setCellStyle(cellStyle);
	    	costCell = costTitleRow.createCell(3);
	    	costCell.setCellValue("Cumulative Cost");
	    	costCell.setCellStyle(cellStyle);
	    	
			double cumulativeCostValue = 0;	
			double cumulativeLength = 0;
			for(CommercialPipeStruct commercialpipe : resultCostsArray)
			{
				Row costRow = costSheet.createRow(costrowindex);
		    	costrowindex++;
				
				costCell = costRow.createCell(0);
				costCell.setCellValue(commercialpipe.diameter);
				costCell.setCellStyle(doubleStyle);
				
				costCell = costRow.createCell(1);
				costCell.setCellValue(commercialpipe.length);
				costCell.setCellStyle(integerStyle);
				
				costCell = costRow.createCell(2);
				costCell.setCellValue(commercialpipe.cost);
				costCell.setCellStyle(integerStyle);
				
				cumulativeCostValue += commercialpipe.cost;
				cumulativeLength += commercialpipe.length;
				costCell = costRow.createCell(3);
				costCell.setCellValue(cumulativeCostValue);
				costCell.setCellStyle(integerStyle);
			}
			Row costTotalRow = costSheet.createRow(costrowindex);
	    	//costrowindex++;
	    	costCell = costTotalRow.createCell(0);
	    	costCell.setCellValue("Total");
	    	costCell.setCellStyle(cellStyle);
	    	costCell = costTotalRow.createCell(1);
	    	costCell.setCellValue(cumulativeLength);
	    	costCell.setCellStyle(integerStyle);
	    	costCell = costTotalRow.createCell(2);
	    	costCell.setCellValue(cumulativeCostValue);
	    	costCell.setCellStyle(integerStyle);
			
	    	esrcostrowindex = costrowindex + 2;
	    	esrcoststart = esrcostrowindex;
	    	
	    	double cumulativeEsrCostValue = 0;
	    	if(esrGeneralProperties.esr_enabled){
	    				    		
	    		Sheet esrCostSheet = generalSheet; //wb.createSheet("Cost");
			    
			    if(esrcostrowindex%2==0)
			    	esrcostrowindex++;
			    row = esrCostSheet.createRow(esrcostrowindex);
			    esrCostSheet.addMergedRegion(new CellRangeAddress(esrcostrowindex,esrcostrowindex,0,7));
			    esrcostrowindex++;
		        Cell esrCostCell = row.createCell(0);
		        esrCostCell.setCellValue("COST RESULTS OF ESRS");
		        esrCostCell.setCellStyle(cellStyle);
		    	for(int i=1;i<8;i++)
		    	{
		    		esrCostCell = row.createCell(i);
		    		esrCostCell.setCellStyle(cellStyle);
		    	}
			    esrcoststart = esrcostrowindex;
		    	Row esrCostTitleRow = esrCostSheet.createRow(esrcostrowindex);
		    	esrcostrowindex++;
		    	esrCostCell = esrCostTitleRow.createCell(0);
		    	esrCostCell.setCellValue("ESR Node ID");
		    	esrCostCell.setCellStyle(cellStyle);
		    	esrCostCell = esrCostTitleRow.createCell(1);
		    	esrCostCell.setCellValue("ESR Child ID");
		    	esrCostCell.setCellStyle(cellStyle);
		    	esrCostCell = esrCostTitleRow.createCell(2);
		    	esrCostCell.setCellValue("Node Name");
		    	esrCostCell.setCellStyle(cellStyle);
		    	esrCostCell = esrCostTitleRow.createCell(3);
		    	esrCostCell.setCellValue("Elevation (m)");
		    	esrCostCell.setCellStyle(cellStyle);
		    	esrCostCell = esrCostTitleRow.createCell(4);
		    	esrCostCell.setCellValue("Capacity (l)");
		    	esrCostCell.setCellStyle(cellStyle);
		    	esrCostCell = esrCostTitleRow.createCell(5);
		    	esrCostCell.setCellValue("ESR Height (m)");
		    	esrCostCell.setCellStyle(cellStyle);
		    	esrCostCell = esrCostTitleRow.createCell(6);
		    	esrCostCell.setCellValue("Cost (Rs)");
		    	esrCostCell.setCellStyle(cellStyle);
		    	esrCostCell = esrCostTitleRow.createCell(7);
		    	esrCostCell.setCellValue("Cumulative Cost (Rs)");
		    	esrCostCell.setCellStyle(cellStyle);
		    	
				
				for(ResultEsrStruct esr : resultEsrsArray)
				{
					Row esrCostRow = esrCostSheet.createRow(esrcostrowindex);
					esrcostrowindex++;
					
					esrCostCell = esrCostRow.createCell(0);
					esrCostCell.setCellValue(esr.nodeid);
					esrCostCell.setCellStyle(integerStyle);
					
					esrCostCell = esrCostRow.createCell(1);
					esrCostCell.setCellStyle(integerStyle);
					
					esrCostCell = esrCostRow.createCell(2);
					esrCostCell.setCellValue(esr.nodename);
					esrCostCell.setCellStyle(integerStyle);
					
					esrCostCell = esrCostRow.createCell(3);
					esrCostCell.setCellValue(esr.elevation);
					esrCostCell.setCellStyle(integerStyle);
					
					esrCostCell = esrCostRow.createCell(4);
					esrCostCell.setCellValue(esr.capacity);
					esrCostCell.setCellStyle(integerStyle);
					
					esrCostCell = esrCostRow.createCell(5);
					esrCostCell.setCellValue(esr.esrheight);
					esrCostCell.setCellStyle(integerStyle);
					
					esrCostCell = esrCostRow.createCell(6);
					esrCostCell.setCellValue(esr.cost);
					esrCostCell.setCellStyle(integerStyle);
					
					esrCostCell = esrCostRow.createCell(7);
					esrCostCell.setCellValue(esr.cumulativecost);
					esrCostCell.setCellStyle(integerStyle);	
					
					cumulativeEsrCostValue = esr.cumulativecost;
					
					int noofchildren = 0;
					for(NodeStruct node : resultNodesArray){
						if(node.esr == esr.nodeid && node.dailydemand>0)
							noofchildren++;
					}
					
					if(noofchildren > 1){
						for(NodeStruct node : resultNodesArray){
							
							if(node.esr == esr.nodeid && node.dailydemand>0){
								esrCostRow = esrCostSheet.createRow(esrcostrowindex);
								esrcostrowindex++;
								
								esrCostCell = esrCostRow.createCell(0);
								esrCostCell.setCellStyle(integerStyle);
								
								esrCostCell = esrCostRow.createCell(1);
								esrCostCell.setCellValue(node.nodeid);
								esrCostCell.setCellStyle(integerStyle);
								
								esrCostCell = esrCostRow.createCell(2);
								esrCostCell.setCellValue(node.nodename);
								esrCostCell.setCellStyle(integerStyle);
								
								esrCostCell = esrCostRow.createCell(3);
								esrCostCell.setCellValue(node.elevation);
								esrCostCell.setCellStyle(integerStyle);
								
								esrCostCell = esrCostRow.createCell(4);
								esrCostCell.setCellValue(node.dailydemand);
								esrCostCell.setCellStyle(integerStyle);
								
								esrCostCell = esrCostRow.createCell(5);
								esrCostCell.setCellStyle(integerStyle);							
								
								esrCostCell = esrCostRow.createCell(6);
								esrCostCell.setCellStyle(integerStyle);
								
								esrCostCell = esrCostRow.createCell(7);
								esrCostCell.setCellStyle(integerStyle);
							}
						}
					}
				}	    		
	    	}
	    	
	    	pumpcostrowindex = esrcostrowindex + 2;
	    	pumpcoststart = pumpcostrowindex;
	    	
	    	double cumulativePumpCostValue = 0;
	    	if(pumpGeneralProperties.pump_enabled){
	    				    		
	    		Sheet pumpCostSheet = generalSheet;
			    
			    if(pumpcostrowindex%2==0)
			    	pumpcostrowindex++;
			    row = pumpCostSheet.createRow(pumpcostrowindex);
			    pumpCostSheet.addMergedRegion(new CellRangeAddress(pumpcostrowindex,pumpcostrowindex,0,5));
			    pumpcostrowindex++;
		        Cell pumpCostCell = row.createCell(0);
		        pumpCostCell.setCellValue("COST RESULTS OF PUMPS");
		        pumpCostCell.setCellStyle(cellStyle);
		    	for(int i=1;i<6;i++){
		    		pumpCostCell = row.createCell(i);
		    		pumpCostCell.setCellStyle(cellStyle);
		    	}
		    	pumpcoststart = pumpcostrowindex;
		    	Row pumpCostTitleRow = pumpCostSheet.createRow(pumpcostrowindex);
		    	pumpcostrowindex++;
		    	pumpCostCell = pumpCostTitleRow.createCell(0);
		    	pumpCostCell.setCellValue("Pipe ID");
		    	pumpCostCell.setCellStyle(cellStyle);
		    	pumpCostCell = pumpCostTitleRow.createCell(1);
		    	pumpCostCell.setCellValue("Pump Head (m)");
		    	pumpCostCell.setCellStyle(cellStyle);
		    	pumpCostCell = pumpCostTitleRow.createCell(2);
		    	pumpCostCell.setCellValue("Pump Power (kW)");
		    	pumpCostCell.setCellStyle(cellStyle);
		    	pumpCostCell = pumpCostTitleRow.createCell(3);
		    	pumpCostCell.setCellValue("Energy Cost (Rs)");
		    	pumpCostCell.setCellStyle(cellStyle);
		    	pumpCostCell = pumpCostTitleRow.createCell(4);
		    	pumpCostCell.setCellValue("Capital Cost (Rs)");
		    	pumpCostCell.setCellStyle(cellStyle);
		    	pumpCostCell = pumpCostTitleRow.createCell(5);
		    	pumpCostCell.setCellValue("Total Cost (Rs)");
		    	pumpCostCell.setCellStyle(cellStyle);
		  			
				for(ResultPumpStruct pump : resultPumpsArray){
					Row pumpCostRow = pumpCostSheet.createRow(pumpcostrowindex);
					pumpcostrowindex++;
					
					pumpCostCell = pumpCostRow.createCell(0);
					pumpCostCell.setCellValue(pump.pipeid);
					pumpCostCell.setCellStyle(integerStyle);
					
					pumpCostCell = pumpCostRow.createCell(1);
					pumpCostCell.setCellValue(pump.pumphead);
					pumpCostCell.setCellStyle(integerStyle);
					
					pumpCostCell = pumpCostRow.createCell(2);
					pumpCostCell.setCellValue(pump.pumppower);
					pumpCostCell.setCellStyle(integerStyle);
					
					pumpCostCell = pumpCostRow.createCell(3);
					pumpCostCell.setCellValue(pump.energycost);
					pumpCostCell.setCellStyle(integerStyle);
					
					pumpCostCell = pumpCostRow.createCell(4);
					pumpCostCell.setCellValue(pump.capitalcost);
					pumpCostCell.setCellStyle(integerStyle);
					
					pumpCostCell = pumpCostRow.createCell(5);
					pumpCostCell.setCellValue(pump.totalcost);
					pumpCostCell.setCellStyle(integerStyle);
					
					cumulativePumpCostValue += pump.totalcost;
				}
	    	}
	    	
			row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	rowindex++;
			generalCell = row.createCell(0);
			generalCell.setCellValue("Total Length of Network");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	generalCell.setCellValue(cumulativeLength+cumulativeOldLength);
	    	generalCell.setCellStyle(integerStyle);
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	rowindex++;
			generalCell = row.createCell(0);
			generalCell.setCellValue("Total Length of New Pipes");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	generalCell.setCellValue(cumulativeLength);
	    	generalCell.setCellStyle(integerStyle);
	    	
	    	row = generalSheet.createRow(rowindex);
	    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
	    	rowindex++;
			generalCell = row.createCell(0);
			generalCell.setCellValue("Total Pipe Cost");
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(1);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(2);
	    	generalCell.setCellStyle(cellStyle);
	    	generalCell = row.createCell(3);
	    	generalCell.setCellValue(cumulativeCostValue);
	    	generalCell.setCellStyle(integerStyle);
	    	
	    	boolean additionalcost = false;
	    	if(esrGeneralProperties.esr_enabled){
	    		row = generalSheet.createRow(rowindex);
		    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
		    	rowindex++;
				generalCell = row.createCell(0);
				generalCell.setCellValue("Total ESR Cost");
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(1);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(2);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(3);
		    	generalCell.setCellValue(cumulativeEsrCostValue);
		    	generalCell.setCellStyle(integerStyle);
		    	additionalcost = true;
	    	}
	    	
	    	if(pumpGeneralProperties.pump_enabled){
	    		row = generalSheet.createRow(rowindex);
		    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
		    	rowindex++;
				generalCell = row.createCell(0);
				generalCell.setCellValue("Total Pump Cost");
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(1);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(2);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(3);
		    	generalCell.setCellValue(cumulativePumpCostValue);
		    	generalCell.setCellStyle(integerStyle);
		    	additionalcost = true;
	    	}
	    	
	    	if(additionalcost){
		    	row = generalSheet.createRow(rowindex);
		    	generalSheet.addMergedRegion(new CellRangeAddress(rowindex,rowindex,0,2));
		    	rowindex++;
				generalCell = row.createCell(0);
				generalCell.setCellValue("Total Cost");
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(1);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(2);
		    	generalCell.setCellStyle(cellStyle);
		    	generalCell = row.createCell(3);
		    	generalCell.setCellValue(cumulativeCostValue+cumulativeEsrCostValue+cumulativePumpCostValue);
		    	generalCell.setCellStyle(integerStyle);
	    	}
	    	
	    	SheetConditionalFormatting sheetCF = generalSheet.getSheetConditionalFormatting();
	
	        ConditionalFormattingRule rule1 = sheetCF.createConditionalFormattingRule("MOD(ROW(),2)");
	        PatternFormatting fill1 = rule1.createPatternFormatting();
	        fill1.setFillBackgroundColor(IndexedColors.PALE_BLUE.index);
	        fill1.setFillPattern(PatternFormatting.SOLID_FOREGROUND);
	        
	        CellRangeAddress[] regions = {
	                CellRangeAddress.valueOf("A"+generalstart+":D"+rowindex), 
	                CellRangeAddress.valueOf("A"+nodestart+":G"+noderowindex),
	                CellRangeAddress.valueOf("A"+pipestart+":O"+piperowindex),
	                CellRangeAddress.valueOf("A"+coststart+":D"+costrowindex)	                
	        };	        
	        sheetCF.addConditionalFormatting(regions, rule1);
	    	
	        if(esrGeneralProperties.esr_enabled){
		        CellRangeAddress[] esrregion = {
		        		CellRangeAddress.valueOf("A"+esrcoststart+":H"+esrcostrowindex)
		        };
		        sheetCF.addConditionalFormatting(esrregion, rule1);
	        }
	        
	        if(pumpGeneralProperties.pump_enabled){
		        CellRangeAddress[] pumpregion = {
		        		CellRangeAddress.valueOf("A"+pumpcoststart+":F"+pumpcostrowindex)
		        };
		        sheetCF.addConditionalFormatting(pumpregion, rule1);
	        }
	        
	        
	        for(int i=0;i<14;i++)
	        	generalSheet.autoSizeColumn(i);
	        
	    	wb.write(os);
	        wb.close();
	        
	        os.flush();
	        os.close();
    	}
    	catch (Exception e) 
		{
			System.out.println(e);
		}
    }
    //generate and upload image snapshot of map tab
    protected void uploadMapSnapshotFile(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
    	try{
    		OutputStream os = response.getOutputStream();
    		
    		response.setContentType("image/png"); // Set up mime type
			response.setHeader("Content-Disposition", "attachment; filename=map_image.png");
	
    		
    		String mapSnapshotString = request.getParameter("imagestring");
			
    		byte[] imagedata = DatatypeConverter.parseBase64Binary(mapSnapshotString.substring(mapSnapshotString.indexOf(",") + 1));
    		BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imagedata));
    		ImageIO.write(bufferedImage, "png", os);
    		
    		
    		os.flush();
	        os.close();
    	}
    	catch (Exception e){
			System.out.println(e);
		}
    }
    
    /*
	protected void doPost_old(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException 
	{
		String t = request.getParameter("action");
		if(t!=null)
		{
			if(t.equalsIgnoreCase("saveInputXml"))
			{
				uploadXmlInputFile(request,response);	
				return;
			}
			else if(t.equalsIgnoreCase("saveInputExcel"))
			{
				uploadExcelInputFile(request,response);	
				return;
			}
			else if(t.equalsIgnoreCase("saveOutputExcel"))
			{
				uploadExcelOutputFile(request,response);
				return;
			}
		}
		
		PrintWriter out = response.getWriter();
		Node.reset();
		Pipe.reset();
		try
		{
			String nameOfProject = request.getParameter("record[name_project]");
			Node.setNameOfProject(nameOfProject);
			Enumeration<String> temp = request.getParameterNames();
			while(temp.hasMoreElements())
			{
				System.out.println(temp.nextElement());
			}
			String minNodePressureString = request.getParameter("record[min_node_pressure]");
			double minNodePressure = Double.valueOf(minNodePressureString);
			Node.setDefaultResidualPressure(minNodePressure);

			String defRoughnessString = request.getParameter("record[def_pipe_roughness]");
			double defRoughness = Double.valueOf(defRoughnessString);
			Pipe.setDefaultRoughness(defRoughness);
			
			String minHLperKMString = request.getParameter("record[min_hl_perkm]");
			double minHLperKM = Double.valueOf(minHLperKMString);
			Pipe.setminHeadLossPerKM(minHLperKM);
			
			String maxHLperKMString = request.getParameter("record[max_hl_perkm]");
			double maxHLperKM = Double.valueOf(maxHLperKMString);
			Pipe.setmaxHeadLossPerKM(maxHLperKM);
			
			String supplyHoursString = request.getParameter("record[supply_hours]");
			double supplyHours = Double.valueOf(supplyHoursString);
			Node.setPeakFactor(24/supplyHours);
			
			String sourceNodeIDString = request.getParameter("record[source_nodeid]");
			int sourceNodeID = Integer.valueOf(sourceNodeIDString);
			
			String sourceNodeName = request.getParameter("record[source_nodename]");
			
			String sourceHeadString = request.getParameter("record[source_head]");
			double sourceHead = Double.valueOf(sourceHeadString);
			
			String sourceElevationString = request.getParameter("record[source_elevation]");
			double sourceElevation = Double.valueOf(sourceElevationString);

			Node n = new Node(sourceElevation,0,sourceNodeID,0,sourceNodeName);
            Node.setSource(n);
			Node.setSourceHGL(sourceHead);
            Node.addNode(n);
			
			Gson gson = new Gson();
			System.out.println(request.getParameter("nodes"));
			NodeStruct[] nodes = gson.fromJson(request.getParameter("nodes"), NodeStruct[].class);
			if(nodes!=null && nodes.length!=0)
			{
				for(NodeStruct node : nodes)
				{
					Node.addNode(new Node(node.elevation, node.demand, node.nodeid, node.minpressure, node.nodename));
				}
			}
			else
				throw new Exception("No node data provided.");
			
			PipeStruct[] pipes = gson.fromJson(request.getParameter("pipes"), PipeStruct[].class);
			if(pipes!=null && pipes.length!=0)
			{
				HashMap<Integer, Node> nodemap = Node.getNodes();
				for(PipeStruct pipe : pipes)
				{
					Node startNode = nodemap.get(pipe.startnode);
	                if(startNode==null)
	                {
	                	throw new Exception("Invalid startNode:" + pipe.startnode + " provided for pipe ID:"+pipe.pipeid);
	                }
	                
	                Node endNode = nodemap.get(pipe.endnode);
	                if(endNode==null)
	                {
	                	throw new Exception("Invalid endNode:" + pipe.endnode + " provided for pipe ID:"+pipe.pipeid);
	                }
					Pipe.addPipe(new Pipe(pipe.length, startNode, endNode, pipe.diameter, pipe.roughness, pipe.pipeid, pipe.parallelallowed));
				}
			}
			else
				throw new Exception("No pipe data provided.");
			
			CommercialPipeStruct[] commercialPipes = gson.fromJson(request.getParameter("commercialPipes"), CommercialPipeStruct[].class);
			if(commercialPipes!=null && commercialPipes.length!=0)
			{
				for(CommercialPipeStruct commercialPipe : commercialPipes)
				{
					Pipe.addPipeCost(commercialPipe.diameter, commercialPipe.cost);
				}
			}
			else
				throw new Exception("No commercial pipe data provided.");
			
			boolean solved = Optimizer.OptimizeFromGUI();
			
			String message;
			if(solved)
			{
				List<NodeStruct> resultNodes = new ArrayList<NodeStruct>();
				for(Node node : Node.getNodes().values())
				{
					NodeStruct resultNode = new NodeStruct(
							node.getNodeID(),
							node.getNodeName(),
							node.getElevation(), 
							node.getDemand(), 
							node.getResidualPressure(), 
							node.getHead(), 
							node.getPressure());
					resultNodes.add(resultNode);	
				}
				
				HashMap<Double, Double> pipeCost = Pipe.getPipeCost();
				TreeMap<Double, Double> cumulativePipeLength = new TreeMap<>();
				List<PipeStruct> resultPipes = new ArrayList<PipeStruct>();
				for(Pipe pipe : Pipe.getPipes().values())
				{
					double dia = pipe.getDiameter();
					double dia2 = pipe.getDiameter2();
					double length = pipe.getLength() - pipe.getLength2();
					if(!pipe.isAllowParallel())
					{
						if(!cumulativePipeLength.containsKey(dia))
							cumulativePipeLength.put(dia, length);
						else
							cumulativePipeLength.put(dia, cumulativePipeLength.get(dia)+length);
					}
					
					double flow = pipe.isAllowParallel() ? 
							pipe.getFlow() / (1 + (Pipe.getDefaultRoughness()/pipe.getRoughness())*Math.pow(dia2/dia, 4.87/1.852)) :
							pipe.getFlow();
					
					double headloss = Util.HWheadLoss(length, flow, pipe.getRoughness(), dia);								
					double headlossperkm = headloss*1000/length;
					
					double cost = length * pipeCost.get(dia);
					if(pipe.isAllowParallel())
						cost = 0;
					
					PipeStruct resultPipe = new PipeStruct(
														 pipe.getPipeID(),
														 pipe.getStartNode().getNodeID(),
														 pipe.getEndNode().getNodeID(),
														 length,
														 dia,
														 pipe.getRoughness(),
														 flow,
														 headloss,
														 headlossperkm,
														 cost,
														 false
														);
					resultPipes.add(resultPipe);
					if(dia2!=0)
					{
						double length2 = pipe.isAllowParallel() ? length : pipe.getLength2();
						if(!cumulativePipeLength.containsKey(dia2))
							cumulativePipeLength.put(dia2, length2);
						else
							cumulativePipeLength.put(dia2, cumulativePipeLength.get(dia2)+length2);
						
						double flow2 = pipe.isAllowParallel() ?
								pipe.getFlow() - flow : 
								pipe.getFlow();
						double headloss2 = Util.HWheadLoss(length2, flow2, pipe.getRoughness(), dia2);								
						double headlossperkm2 = headloss2*1000/length2;
						
						//String allowParallelString = pipe.isAllowParallel() ? "Parallel" : null;
						double cost2 = length2 * pipeCost.get(dia2);
						
						
						resultPipe = new PipeStruct(
												 pipe.getPipeID(),
												 pipe.getStartNode().getNodeID(),
												 pipe.getEndNode().getNodeID(),
												 length2,
												 dia2,
												 pipe.getRoughness(),
												 flow2,
												 headloss2,
												 headlossperkm2,
												 cost2,
												 pipe.isAllowParallel()
													);
						resultPipes.add(resultPipe);
					}
				}
				
				double cumulativeCost = 0;
				List<CommercialPipeStruct> resultCost = new ArrayList<CommercialPipeStruct>();
				for(Entry<Double, Double> entry : cumulativePipeLength.entrySet())
				{		
					double cost = 0;
					if(pipeCost.containsKey(entry.getKey()))
						cost = entry.getValue() * pipeCost.get(entry.getKey());
					cumulativeCost += cost;			
					CommercialPipeStruct resultcommercialPipe = new CommercialPipeStruct(
														 entry.getKey(),
														 cost,
														 entry.getValue(),
														 cumulativeCost,
														 defRoughness
														);	
					resultCost.add(resultcommercialPipe);
				}
				
				String resultNodeString = gson.toJson(resultNodes);
				System.out.println(resultNodeString);
				
				String resultPipeString = gson.toJson(resultPipes);
				System.out.println(resultPipeString);
				
				String resultCostString = gson.toJson(resultCost);
				System.out.println(resultCostString);
				
				message="{\"status\":\"success\", \"data\":\"Done!\", \"resultnodes\":"+resultNodeString+", \"resultpipes\":"+resultPipeString+", \"resultcost\":"+resultCostString+"}";		
			}
			else
			{
				message="{\"status\":\"error\",\"message\":\"Failed to solve network\"}";
			}
			out.print(message);
		}
		catch(Exception e)
		{
			System.out.println(e.getMessage());
			String error="{\"status\":\"error\",\"message\":\""+e.getMessage()+"\"}";
			out.print(error);
		}
	}
*/
	//handle request from JalTantra site
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException 
	{		
    	//action refers to uploading some file or optimization of network
		String t = request.getParameter("action");
		if(t!=null)
		{
			if(t.equalsIgnoreCase("saveInputXml"))
			{
				uploadXmlInputFile(request,response);	
				return;
			}
			else if(t.equalsIgnoreCase("saveInputExcel"))
			{
				uploadExcelInputFile(request,response);	
				return;
			}
			else if(t.equalsIgnoreCase("saveOutputExcel"))
			{
				uploadExcelOutputFile(request,response);
				return;
			}
			else if(t.equalsIgnoreCase("saveOutputEpanet"))
			{
				uploadEpanetOutputFile(request,response);
				return;
			}
			else if(t.equalsIgnoreCase("saveMapSnapshot"))
			{
				uploadMapSnapshotFile(request,response);
				return;
			}
		}
		
		PrintWriter out = response.getWriter();
		try{
			String place = request.getParameter("place");
			if(place!=null){
				request.setAttribute("place", place);
			}
			
			String currVersion = version;
			String clientVersion = request.getParameter("version");
			boolean clientOld = compareVersion(currVersion,clientVersion) != 0;
			
			if(clientOld){
				throw new Exception("Your browser is running an old JalTantra version.<br> Please save your data and press ctrl+F5 to do a hard refresh and get the latest version.<br> If still facing issues please contact the <a target='_blank' href='https://groups.google.com/forum/#!forum/jaltantra-users/join'>JalTantra Google Group</a>");
			}
			
			Gson gson = new Gson();
			GeneralStruct generalProperties = gson.fromJson(request.getParameter("general"), GeneralStruct.class);
			
			String project = generalProperties.name_project;
			if(project!=null){
				request.setAttribute("project", project);
			}
			
			String organization = generalProperties.name_organization;
			if(organization!=null){
				request.setAttribute("organization", organization);
			}
			
			//System.out.println(request.getParameter("nodes"));
			NodeStruct[] nodes = gson.fromJson(request.getParameter("nodes"), NodeStruct[].class);
			if(nodes==null || nodes.length==0)
				throw new Exception("No node data provided.");
			
			PipeStruct[] pipes = gson.fromJson(request.getParameter("pipes"), PipeStruct[].class);
			if(pipes==null || pipes.length==0)
				throw new Exception("No pipe data provided.");
			
			CommercialPipeStruct[] commercialPipes = gson.fromJson(request.getParameter("commercialPipes"), CommercialPipeStruct[].class);
			if(commercialPipes==null || commercialPipes.length==0)
				throw new Exception("No commercial pipe data provided.");
			
			EsrGeneralStruct esrGeneralProperties = gson.fromJson(request.getParameter("esrGeneral"), EsrGeneralStruct.class);
			EsrCostStruct[] esrCostsArray = gson.fromJson(request.getParameter("esrCost"), EsrCostStruct[].class);
			
			PumpGeneralStruct pumpGeneralProperties = gson.fromJson(request.getParameter("pumpGeneral"), PumpGeneralStruct.class);
			PumpManualStruct[] pumpManualArray = gson.fromJson(request.getParameter("pumpManual"), PumpManualStruct[].class);
			
			ValveStruct[] valves = gson.fromJson(request.getParameter("valves"), ValveStruct[].class);
			
			optimizer.Optimizer opt = new optimizer.Optimizer(nodes, pipes, commercialPipes, generalProperties, esrGeneralProperties, esrCostsArray, pumpGeneralProperties, pumpManualArray, valves);
			
			boolean solved = opt.Optimize();
			
			String message;
			if(solved)
			{
				double secondaryFlowFactor = generalProperties.supply_hours/esrGeneralProperties.secondary_supply_hours;
				double esrcapacityfactor = 1;
				if(esrGeneralProperties.esr_enabled){
					esrcapacityfactor = esrGeneralProperties.esr_capacity_factor;
				}
				List<NodeStruct> resultNodes = new ArrayList<NodeStruct>();
				for(optimizer.Node node : opt.getNodes().values())
				{
					double demand = node.getDemand();
					if(node.getESR()!=node.getNodeID())
						demand = demand * secondaryFlowFactor;
					NodeStruct resultNode = new NodeStruct(
							node.getNodeID(),
							node.getNodeName(),
							node.getElevation(), 
							demand,
							node.getRequiredCapacity(esrcapacityfactor),
							node.getResidualPressure(), 
							node.getHead(), 
							node.getPressure(),
							node.getESR());
					resultNodes.add(resultNode);	
				}
				
				TreeMap<PipeCost, Double> cumulativePipeLength = new TreeMap<>();
				List<PipeStruct> resultPipes = new ArrayList<PipeStruct>();
				List<ResultPumpStruct> resultPumps = new ArrayList<ResultPumpStruct>();
				
				
				for(optimizer.Pipe pipe : opt.getPipes().values()){
					double dia = pipe.getDiameter();
					double dia2 = pipe.getDiameter2();
					PipeCost chosenPipeCost = pipe.getChosenPipeCost();
					PipeCost chosenPipeCost2 = pipe.getChosenPipeCost2();
					double length = pipe.getLength() - pipe.getLength2();
					if(!pipe.existingPipe())
					{
						if(!cumulativePipeLength.containsKey(chosenPipeCost))
							cumulativePipeLength.put(chosenPipeCost, length);
						else
							cumulativePipeLength.put(chosenPipeCost, cumulativePipeLength.get(chosenPipeCost)+length);
					}

					double flow = pipe.isAllowParallel() && dia2!=0 ? 
							pipe.getFlow() / (1 + (pipe.getRoughness2()/pipe.getRoughness())*Math.pow(dia2/dia, 4.87/1.852)) :
							pipe.getFlow();
					
					double parallelFlow = pipe.getFlow() - flow;
					if(pipe.getFlowchoice()==FlowType.SECONDARY){
						flow = flow * secondaryFlowFactor;
					}
					
					double headloss = Util.HWheadLoss(length, flow, pipe.getRoughness(), dia);								
					double headlossperkm = headloss*1000/length;
					double speed = Util.waterSpeed(flow, dia); 
					
					double cost; 
					if(pipe.existingPipe())
						cost = 0;
					else
						cost = length * chosenPipeCost.getCost();
					
					optimizer.Node startNode = pipe.getStartNode();
					optimizer.Node endNode = pipe.getEndNode();
					
					boolean pressureExceeded = false;
					if(generalProperties.max_pipe_pressure > 0){
						if(startNode.getPressure() > generalProperties.max_pipe_pressure || 
						   endNode.getPressure() > generalProperties.max_pipe_pressure){
							pressureExceeded = true;
						}
					}
					
					PipeStruct resultPipe = new PipeStruct(
														 pipe.getPipeID(),
														 startNode.getNodeID(),
														 endNode.getNodeID(),
														 length,
														 dia,
														 pipe.getRoughness(),
														 flow,
														 headloss,
														 headlossperkm,
														 speed,
														 cost,
														 false,
														 pressureExceeded,
														 pipe.getFlowchoice()==FlowType.PRIMARY,
														 pipe.getPumpHead(),
														 pipe.getPumpPower(),
														 pipe.getValveSetting()
														);
					resultPipes.add(resultPipe);
					if(dia2!=0)
					{
						double length2 = pipe.isAllowParallel() ? length : pipe.getLength2();
						if(!cumulativePipeLength.containsKey(chosenPipeCost2))
							cumulativePipeLength.put(chosenPipeCost2, length2);
						else
							cumulativePipeLength.put(chosenPipeCost2, cumulativePipeLength.get(chosenPipeCost2)+length2);
						
						double flow2 = pipe.isAllowParallel() ?
								parallelFlow : 
								pipe.getFlow();
						
						if(pipe.getFlowchoice()==FlowType.SECONDARY){
							flow2 = flow2 * secondaryFlowFactor;
						}
						
						double headloss2 = Util.HWheadLoss(length2, flow2, pipe.getRoughness2(), dia2);								
						double headlossperkm2 = headloss2*1000/length2;
						double speed2 = Util.waterSpeed(flow2, dia2);
						//String allowParallelString = pipe.isAllowParallel() ? "Parallel" : null;
						double cost2 = length2 * chosenPipeCost2.getCost();
						
						
						resultPipe = new PipeStruct(
												 pipe.getPipeID(),
												 pipe.getStartNode().getNodeID(),
												 pipe.getEndNode().getNodeID(),
												 length2,
												 dia2,
												 pipe.getRoughness2(),
												 flow2,
												 headloss2,
												 headlossperkm2,
												 speed2,
												 cost2,
												 pipe.isAllowParallel(),
												 pressureExceeded,
												 pipe.getFlowchoice()==FlowType.PRIMARY,
												 pipe.getPumpHead(),
												 pipe.getPumpPower(),
												 pipe.getValveSetting()
													);
						resultPipes.add(resultPipe);
					}
					
					if(pumpGeneralProperties.pump_enabled && pipe.getPumpPower()>0){
						double presentvaluefactor = Util.presentValueFactor(pumpGeneralProperties.discount_rate, pumpGeneralProperties.inflation_rate, pumpGeneralProperties.design_lifetime);		
						double primarycoeffecient = 365*presentvaluefactor*generalProperties.supply_hours*pumpGeneralProperties.energycost_per_kwh*pumpGeneralProperties.energycost_factor;
						double secondarycoeffecient = esrGeneralProperties.esr_enabled ? 365*presentvaluefactor*esrGeneralProperties.secondary_supply_hours*pumpGeneralProperties.energycost_per_kwh*pumpGeneralProperties.energycost_factor : 0;
						double power = pipe.getPumpPower();
						
						double energycost = power* (pipe.getFlowchoice()==FlowType.PRIMARY ? primarycoeffecient : secondarycoeffecient);
						double capitalcost = power*pumpGeneralProperties.capitalcost_per_kw;
						
						
						resultPumps.add(new ResultPumpStruct(pipe.getPipeID(), 
															 pipe.getPumpHead(), 
															 power, 
															 energycost, 
															 capitalcost, 
															 energycost+capitalcost));
					}
				}
				
				double cumulativeCost = 0;
				List<CommercialPipeStruct> resultCost = new ArrayList<CommercialPipeStruct>();
				for(Entry<PipeCost, Double> entry : cumulativePipeLength.entrySet())
				{		
					double cost = 0;
					cost = entry.getValue() * entry.getKey().getCost();
					cumulativeCost += cost;			
					CommercialPipeStruct resultcommercialPipe = new CommercialPipeStruct(
														 entry.getKey().getDiameter(),
														 cost,
														 entry.getValue(),
														 cumulativeCost,
														 entry.getKey().getRoughness()
														);	
					resultCost.add(resultcommercialPipe);
				}
				List<ResultEsrStruct> resultEsrCost = new ArrayList<ResultEsrStruct>();
				if(esrGeneralProperties.esr_enabled){
					double cumulativeEsrCost = 0;
					for(optimizer.Node node : opt.getNodes().values())
					{	
						if(node.getESR()==node.getNodeID() && node.getEsrTotalDemand() > 0){
							double cost = node.getEsrCost();
							cumulativeEsrCost += cost;	
							
							boolean hasprimarychild = false;
							for(Pipe pipe : node.getOutgoingPipes()){
								if(pipe.getFlowchoice()==FlowType.PRIMARY){
									hasprimarychild = true;
									break;
								}
									
							}
							ResultEsrStruct resultEsr = new ResultEsrStruct(
																 node.getNodeID(), 
																 node.getNodeName(), 
																 node.getElevation(), 
																 node.getEsrHeight(), 
																 node.getEsrTotalDemand(),
																 cost,
																 cumulativeEsrCost,
																 hasprimarychild
																);	
							resultEsrCost.add(resultEsr);
						}
					}
				}
				
				String resultNodeString = gson.toJson(resultNodes);
				//System.out.println(resultNodeString);
				
				String resultPipeString = gson.toJson(resultPipes);
				//System.out.println(resultPipeString);
				
				String resultCostString = gson.toJson(resultCost);
				//System.out.println(resultCostString);
				
				String resultEsrCostString = gson.toJson(resultEsrCost);
				//System.out.println(resultEsrCostString);
				
				String resultPumpString = gson.toJson(resultPumps);
				
				//System.out.println(generateRandomInput());
				//String coordinatesString = opt.getCoordinatesString();
				message="{\"status\":\"success\", \"data\":\"Done!\", \"resultnodes\":"+resultNodeString+", \"resultpipes\":"+resultPipeString+", \"resultcost\":"+resultCostString+", \"resultesrcost\":"+resultEsrCostString+", \"resultpumpcost\":"+resultPumpString+"}";					
				//System.out.println(message);
			}
			else
			{
				message="{\"status\":\"error\",\"message\":\"Failed to solve network\"}";
			}
			out.print(message);
		}
		catch(Exception e)
		{
			System.out.println(e.getMessage());
			String error="{\"status\":\"error\",\"message\":\""+e.getMessage()+"\"}";
			out.print(error);
		}
	}

    // ensure client version is same as server version
	private int compareVersion(String currVersion, String clientVersion) {
		if(clientVersion == null)
            return 1;
        String[] currParts = currVersion.split("\\.");
        String[] clientParts = clientVersion.split("\\.");
        int length = Math.max(currParts.length, clientParts.length);
        for(int i = 0; i < length; i++) {
            int currPart = i < currParts.length ?
                Integer.parseInt(currParts[i]) : 0;
            int clientPart = i < clientParts.length ?
                Integer.parseInt(clientParts[i]) : 0;
            if(currPart < clientPart)
                return -1;
            if(currPart > clientPart)
                return 1;
        }
        return 0;
	}

	//generate a random input network for testing purposes
	@SuppressWarnings("unused")
	private String generateRandomInput(){
	
		
		String nodeString = "<source elevation=\"500.0\" head=\"500.0\" nodeID=\"1\" nodeName=\"a\"/>\n";
		String nodeFormat = "<node demand=\"%f\" elevation=\"%d\" nodeID=\"%d\"/>\n";
		String pipeString = "";
		String pipeFormat = "<pipe startNode=\"%d\" endNode=\"%d\" length=\"%d\"/>\n";
		Queue<Integer> nodesRemaining = new LinkedList<Integer>();
		List<Integer> parent = new ArrayList<Integer>();
		
		int totalnodes = 150;
		int nodenumber = 1;
		nodesRemaining.add(1);
		parent.add(1);
		parent.add(1);
		
		while(nodenumber < totalnodes){
			int numberofchildren = ThreadLocalRandom.current().nextInt(1, 5 + 1);
			numberofchildren = Math.min(numberofchildren, totalnodes-nodenumber);
			
			int currparent = nodesRemaining.remove();
			for(int i=0;i<numberofchildren;i++){
				nodenumber++;
				parent.add(currparent);
				nodesRemaining.add(nodenumber);
			}
		}
		
		for(int i=2; i<=totalnodes;i++){
			int elev = ThreadLocalRandom.current().nextInt(100, 300 + 1);
			double demand = (ThreadLocalRandom.current().nextInt(1, 500 + 1))/100.00;
			boolean isdummy = ThreadLocalRandom.current().nextInt(0, 1 + 1)==1;
			int length = ThreadLocalRandom.current().nextInt(500, 5000 + 1);
			if(nodesRemaining.contains(i))
				isdummy = false;
			if(isdummy)
				demand = 0;
			
			nodeString += String.format(nodeFormat, demand, elev, i);
			pipeString += String.format(pipeFormat, parent.get(i), i, length);
			
		}
		
		return nodeString + pipeString;
	}
	
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException 
	{		
	}
	
}
