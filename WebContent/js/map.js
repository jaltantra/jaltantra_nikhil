"use strict";
var SAMPLES = 512;
var sourceIcon = 'waterdrop.png';
var esrIcon = 'watertower.png';
var markerIcon = 'drinkingwater.png'
var map;
var contextMapMenu;
var contextNodeMenu;
var contextPipeMenu;
var nodes = [];
var pipes = [];
var chart;
var placemarker;
var autocomplete;
var mousemarker = null;
var elevations = null;
var addNode = false;
var delNode = false;
var edNode = false;
var addPipe = false;
var addDirectPipe = false;
var delPipe = false;
var editPipe = false;
//    var editPolyLine = null;
var marker1 = null;
var editNodeMarker;
var sourceMarker = null;
var rightClickMarker;
var rightClickPolyline;
var infoWindow;
var clickedPipe = null;
var splitPipe;  
var elevationService;
var directionsService;
//	var directionsDisplay;
var elevationPipes = [];
var elevationPipe;
var delay = 100;
var encodeString;
var testString;
var testString2;
var data;
var chartOptions2;
var chartPointMultiplier = 2;
  
// Load the Visualization API and the piechart package.
//google.load("visualization", "1", {packages: ["columnchart"]});
//google.load("visualization", "1", {packages: ["corechart"]});


// Set a callback to run when the Google Visualization API is loaded.
//google.setOnLoadCallback(initialize);



// add a right click menu for the map, nodes and pipes
function addContextMenu()
{
	// create the ContextMenuOptions object
	var contextMapMenuOptions={};
	contextMapMenuOptions.classNames={menu:'context_menu', menuSeparator:'context_menu_separator'};
	
	//	create an array of map ContextMenuItem objects
	var mapMenuItems=[];
	mapMenuItems.push({className:'context_menu_item', eventName:'add_node', label:'Add node here'});
	mapMenuItems.push({className:'context_menu_item', eventName:'delete_node', label:'Delete a node'});
	mapMenuItems.push({className:'context_menu_item', eventName:'edit_node', label:'Edit a node'});
	mapMenuItems.push({});
	mapMenuItems.push({className:'context_menu_item', eventName:'add_pipe', label:'Add a pipe'});
	mapMenuItems.push({className:'context_menu_item', eventName:'add_direct_pipe', label:'Add a direct pipe'});
	mapMenuItems.push({className:'context_menu_item', eventName:'delete_pipe', label:'Delete a pipe'});
	//mapMenuItems.push({className:'context_menu_item', eventName:'edit_pipe', label:'Edit a pipe'});
	mapMenuItems.push({className:'context_menu_item', eventName:'split_pipe', label:'Split a pipe'});
	mapMenuItems.push({});
	mapMenuItems.push({className:'context_menu_item', eventName:'close_chart', label:'Close the elevation chart'});
			
	contextMapMenuOptions.menuItems=mapMenuItems;
	
	//	create the ContextMenu object
	contextMapMenu = new ContextMenu(map, contextMapMenuOptions);
	
//		display the ContextMenu on a Map right click
	google.maps.event.addListener(map, 'rightclick', function(mouseEvent){
		contextNodeMenu.hide();
		contextPipeMenu.hide();
		contextMapMenu.show(mouseEvent.latLng);
	});
	
	//	listen for the ContextMenu 'menu_item_selected' event
	google.maps.event.addListener(contextMapMenu, 'menu_item_selected', function(latLng, eventName){
		//	latLng is the position of the ContextMenu
		//	eventName is the eventName defined for the clicked ContextMenuItem in the ContextMenuOptions
		
		addNode = false;
		delNode = false;
		edNode = false;
		addPipe = false;
		addDirectPipe = false;
		delPipe = false;
		editPipe = false;
		splitPipe = false;
		marker1 = null;
					
		switch(eventName){
			case 'add_node':
				addMarker(latLng);
				break;
			case 'delete_node':
				delNode = true;
				break;
			case 'edit_node':
				edNode = true;
				break;
			case 'add_pipe':
				addPipe = true;
				break;
			case 'add_direct_pipe':
				addPipe = true;
				addDirectPipe = true;
				break;
			case 'delete_pipe':
				delPipe = true;
				break;
// 				case 'edit_pipe':
// 					var editMapMenuItemDiv = $(".context_menu_item:contains('Edit a pipe')");
// 					var editPipeMenuItemDiv = $(".context_menu_item:contains('Edit the pipe')");
				
// 					if(editMapMenuItemDiv.length==0)
// 					{
// 						pipeEditDone();
// 					}
// 					else
// 					{
// 						editPipe = true;
// 						editMapMenuItemDiv.text('End Editing');
// 						editPipeMenuItemDiv.text('End editing the pipe')
// 					}
// 					break;	
			case 'split_pipe':
				splitPipe = true;
				break;
			case 'close_chart':
				//$( "#chart_div" ).hide();
				closeChart();
				break;
		}
	});
	
	var contextNodeMenuOptions={};
	contextNodeMenuOptions.classNames={menu:'context_menu', menuSeparator:'context_menu_separator'};
	
	//	create an array of node ContextMenuItem objects
	var nodeMenuItems=[];
	nodeMenuItems.push({className:'context_menu_item', eventName:'delete_node', label:'Delete the node'});
	nodeMenuItems.push({className:'context_menu_item', eventName:'edit_node', label:'Edit the node'});
			
	contextNodeMenuOptions.menuItems=nodeMenuItems;
	
	//	create the ContextMenu object
	contextNodeMenu = new ContextMenu(map, contextNodeMenuOptions);
			
	//	listen for the ContextMenu 'menu_item_selected' event
	google.maps.event.addListener(contextNodeMenu, 'menu_item_selected', function(latLng, eventName){
		//	latLng is the position of the ContextMenu
		//	eventName is the eventName defined for the clicked ContextMenuItem in the ContextMenuOptions
		
		addNode = false;
		delNode = false;
		edNode = false;
		addPipe = false;
		addDirectPipe = false;
		delPipe = false;
		editPipe = false;
		splitPipe = false;
		marker1 = null;
					
		switch(eventName){
			case 'delete_node':
				deleteNode(rightClickMarker);
				break;
			case 'edit_node':
				editNode(rightClickMarker);
				break;
		}
	});
	
	var contextPipeMenuOptions={};
	contextPipeMenuOptions.classNames={menu:'context_menu', menuSeparator:'context_menu_separator'};
	
	//	create an array of pipe ContextMenuItem objects
	var pipeMenuItems=[];
	pipeMenuItems.push({className:'context_menu_item', eventName:'delete_pipe', label:'Delete the pipe'});
//		pipeMenuItems.push({className:'context_menu_item', eventName:'edit_pipe', label:'Edit the pipe'});
	pipeMenuItems.push({className:'context_menu_item', eventName:'split_pipe', label:'Split the pipe'});
	pipeMenuItems.push({});
	pipeMenuItems.push({className:'context_menu_item', eventName:'close_chart', label:'Close the elevation chart'});
	
	
	contextPipeMenuOptions.menuItems=pipeMenuItems;
	
	//	create the ContextMenu object
	contextPipeMenu = new ContextMenu(map, contextPipeMenuOptions);
	
	//	listen for the ContextMenu 'menu_item_selected' event
	google.maps.event.addListener(contextPipeMenu, 'menu_item_selected', function(latLng, eventName){
		//	latLng is the position of the ContextMenu
		//	eventName is the eventName defined for the clicked ContextMenuItem in the ContextMenuOptions
		
		addNode = false;
		delNode = false;
		edNode = false;
		addPipe = false;
		addDirectPipe = false;
		delPipe = false;
		editPipe = false;
		splitPipe = false;
		marker1 = null;
			    	 			
		var newMarker;
		
		switch(eventName){
			case 'delete_pipe':
				deletePipe(rightClickPolyline);
				break;
// 				case 'edit_pipe':
// 					var editPipeMenuItemDiv = $(".context_menu_item:contains('Edit the pipe')");
// 					var editMapMenuItemDiv = $(".context_menu_item:contains('Edit a pipe')");
				
// 					if(editPipeMenuItemDiv.length==0)
// 					{
// 						pipeEditDone();
// 					}
// 					else
// 					{
// 						editPipeMenuItemDiv.text('End editing the pipe');
// 						editMapMenuItemDiv.text('End editing');
// 						rightClickPolyline.setEditable(true);
// 			  		  	editPolyLine = rightClickPolyline;
// 			  		  	infoWindow.setContent('<input type="button" onclick="pipeEditDone()" value ="Done Editing"/>'
// 			  					);
// 			  			infoWindow.open(map, rightClickPolyline.destination);
// 					}		  			
// 					break;	
			case 'split_pipe':
				newMarker = addMarker(latLng);
	  		  	addRoute(rightClickPolyline.origin, newMarker);
	  		  	addRoute(newMarker, rightClickPolyline.destination);
	  		  	deletePipe(rightClickPolyline);
				break;
			case 'close_chart':
				closeChart();
				break;
		}
	});
}

function closeChart()
{
	//$( "#chart_div" ).hide();
	$('#chart_status').val('hide');
    $('#chart_status').change();
	if(clickedPipe!==null)
	{
	    clickedPipe.setOptions({
	        strokeWeight: 2
	    });
		clickedPipe = null;
	}
}

//create an add node button
function OptionsControl1(controlDiv, map) {

	// Set CSS for the control border.
	var addNodeUI = document.createElement('div');
	addNodeUI.style.backgroundColor = '#fff';
	addNodeUI.style.border = '2px solid #fff';
	addNodeUI.style.borderRight = '1px solid #C0C0C0';
	addNodeUI.style.borderRadius = '3px';
	addNodeUI.style.boxShadow = '2px 2px 6px rgba(0,0,0,.3)';
	addNodeUI.style.cursor = 'pointer';
	addNodeUI.style.marginBottom = '22px';
	addNodeUI.style.textAlign = 'center';
	addNodeUI.title = 'Click to add node';
	controlDiv.appendChild(addNodeUI);

	// Set CSS for the control interior.
	var addNodeText = document.createElement('div');
	addNodeText.style.color = 'rgb(25,25,25)';
	addNodeText.style.fontFamily = 'Roboto,Arial,sans-serif';
	addNodeText.style.fontSize = '16px';
	addNodeText.style.lineHeight = '38px';
	addNodeText.style.paddingLeft = '5px';
	addNodeText.style.paddingRight = '5px';
	addNodeText.innerHTML = 'Add node';
	addNodeUI.appendChild(addNodeText);
  	// Setup the click event listeners
	addNodeUI.addEventListener('click', function() {
		addNode = true;
		delNode = false;
		edNode = false;
		addPipe = false;
		addDirectPipe = false;
		delPipe = false;
		editPipe = false;
		splitPipe = false;
		marker1 = null;
	});
}

//create an add pipe button
function OptionsControl2(controlDiv, map) {

	// Set CSS for the control border.
	var addPipeUI = document.createElement('div');
	addPipeUI.style.backgroundColor = '#fff';
	addPipeUI.style.border = '2px solid #fff';
	addPipeUI.style.borderLeft = '1px solid #C0C0C0';
	addPipeUI.style.borderRadius = '3px';
	addPipeUI.style.boxShadow = '2px 2px 6px rgba(0,0,0,.3)';
	addPipeUI.style.cursor = 'pointer';
	addPipeUI.style.marginBottom = '22px';
	addPipeUI.style.textAlign = 'center';
	addPipeUI.title = 'Click to add pipe';
	controlDiv.appendChild(addPipeUI);

	// Set CSS for the control interior.
	var addPipeText = document.createElement('div');
	addPipeText.style.color = 'rgb(25,25,25)';
	addPipeText.style.fontFamily = 'Roboto,Arial,sans-serif';
	addPipeText.style.fontSize = '16px';
	addPipeText.style.lineHeight = '38px';
	addPipeText.style.paddingLeft = '5px';
	addPipeText.style.paddingRight = '5px';
	addPipeText.innerHTML = 'Add pipe';
	addPipeUI.appendChild(addPipeText);
  
	// Setup the click event listeners
	addPipeUI.addEventListener('click', function() {			  
		addNode = false;
		delNode = false;
		edNode = false;
		addPipe = true;
		addDirectPipe = false;
		delPipe = false;
		editPipe = false;
		splitPipe = false;
		marker1 = null;
	});
}

//function to handle geolocation error
//function handleLocationError(browserHasGeolocation, pos) { }

// Adds a marker to the map.
function addMarker(location, editPopup, id, name) {
	
	if(editPopup === undefined)
	{
		editPopup = true;
	}
	if(id === undefined)
	{
		id = getNodeID();
	}
	if(name === undefined)
	{
		name = "Node"+id;
	}

	// Add the marker at the location
	var marker = new MarkerWithLabel({
		position: location,
		map: map,
		draggable: true,
		animation: google.maps.Animation.DROP,
		title: name,
		icon: markerIcon,
		labelContent: name,
		labelAnchor: new google.maps.Point(0, 0),
		labelClass: "labels", // the CSS class for the label
		labelStyle: {opacity: 0.75}
	});
  
	marker.nodeID = id;
	//console.log(marker.nodeID);
	marker.inPipes = [];
	marker.outPipes = [];
	
	//nodes is global list of all nodes
	nodes.push(marker);
  	
	// event listener for left click, used to track pipe addition between nodes and deletion of nodes
	marker.addListener('click', function(){
		// if addPipe is true mark this node as marker1 or marker2		
		if(addPipe)
		{
			if(marker1===null)
			{	
				marker1 = marker;
			}
			else
			{
				var marker2 = marker;
				if(marker1!==marker2)
				{
					addRoute(marker1, marker2, addDirectPipe);
					marker1 = null;
					addPipe = false;
					addDirectPipe = false;
				}
			}
		}
		else if(delNode)
		{
			delNode = false;
			deleteNode(this);
		}
		else if(edNode)
		{
			edNode = false;
			editNode(this);
		}
	});

	// display the ContextMenu on a node right click
	marker.addListener('rightclick', function(mouseEvent){
		contextPipeMenu.hide();
		contextMapMenu.hide();
		contextNodeMenu.show(mouseEvent.latLng);
		rightClickMarker = this;
	});
  
	//update connected pipes after changing node position
	marker.addListener('dragend', function(){
		updateMarkerElevation(marker, false);
		updateMarkerPipes(this);
	});
  
	//get elevation of the added marker
	updateMarkerElevation(marker, editPopup);
	return marker;
}

function updateMarkerElevation(marker, popup)
{
	var msg;
	elevationService.getElevationForLocations({'locations':[marker.getPosition()]}, 
			function(results, status) {
				var err = false;
				if (status === google.maps.ElevationStatus.OK) {
				// Retrieve the first result
					if (results[0]) {
						msg = results[0].elevation + ' meters.';
						marker.elevation = results[0].elevation; 
						if(popup)
						{
							editNode(marker);
						}
					} else {
						msg = 'Could not get elevation.';
						err = true;
					}
				} else {
					msg = 'Elevation service failed due to: ' + status;
					err = true;
				}
				if(err)
				{
					alert(msg);
				}
				console.log(msg);
			});
}

// update all the incoming and outgoing pipes of a marker
function updateMarkerPipes(marker)
{
	var i;
	var pipe;
	var path;
	var index;
	var position = marker.getPosition();
	for(i = 0; i < marker.inPipes.length; i++)
	{
		pipe = marker.inPipes[i];
		path = pipe.getPath();
		index = path.length - 1;
		path.setAt(index,position);		
		updateElevation(pipe);
		//elevationPipes.push(pipe);				
	}
	for(i = 0; i < marker.outPipes.length; i++)
	{
		pipe = marker.outPipes[i];
		path = pipe.getPath();
  		index = 0;
		path.setAt(index,position);	
		updateElevation(pipe);
		//elevationPipes.push(pipe);
	}
	//getMultipleElevationResultsHelper();
}

// helper function to get elevation of a list of paths
function getMultipleElevationResultsHelper()
{
	setTimeout(getMultipleElevationResults,delay);
}

// gets the elevation for one path in the global list of paths for which elevation needs to be queried
function getMultipleElevationResults()
{
	if(elevationPipes.length > 0)
	{
		elevationPipe = elevationPipes.pop();
		var path = elevationPipe.getPath();
		elevationService.getElevationAlongPath({
			path: path.getArray(),
			samples: SAMPLES
			}, function(results,status) {
				if(status==google.maps.ElevationStatus.OVER_QUERY_LIMIT)
	        	{
	        		elevationPipes.push(elevationPipe);
	        		delay = 2 * delay;
	        		console.log('New delay value: '+delay+'ms.');
	        	}
	        	else
        		{
	        		elevationPipe.elevationResults = results;
					if(clickedPipe!==null && elevationPipe===clickedPipe)
	        			plotElevation(elevationPipe);
					delay = 100;
	        	}
	        	getMultipleElevationResultsHelper();
    		});
	}
}

function deleteAllNodes()
{
	var i;
	var j;
	var marker;
	for(j = 0; j < nodes.length; j++)
	{
		marker = nodes[j];
		for(i = 0; i < marker.inPipes.length; i++)
		{
			deletePipe(marker.inPipes[i]);
		}
		for(i = 0; i < marker.outPipes.length; i++)
		{
			deletePipe(marker.outPipes[i]);
		}		
		marker.setMap(null);
	}		
	nodes = [];
}

// deletes a node and all pipes connected to this node
function deleteNode(marker)
{
	var i;
	for(i = 0; i < nodes.length; i++)
	{
		if(marker == nodes[i])
		{
			nodes.splice( i, 1 );
			break;
		}
	}		
	for(i = 0; i < marker.inPipes.length; i++)
	{
		deletePipe(marker.inPipes[i]);
	}
	for(i = 0; i < marker.outPipes.length; i++)
	{
		deletePipe(marker.outPipes[i]);
	}		
	marker.setMap(null);
}

//deletes a pipe
function deletePipe(polyline)
{
	var i;
	for(i = 0; i < pipes.length; i++)
	{
		if(polyline == pipes[i])
		{
			pipes.splice( i, 1 );
			break;
		}
	}
	
	for(i = 0; i < polyline.origin.outPipes.length; i++)
	{
		if(polyline == polyline.origin.outPipes[i])
		{
			polyline.origin.outPipes.splice( i, 1 );
			break;
		}
	}
	
	for(i = 0; i < polyline.destination.inPipes.length; i++)
	{
		if(polyline == polyline.destination.inPipes[i])
		{
			polyline.destination.inPipes.splice( i, 1 );
			break;
		}
	}
	
	if(clickedPipe===polyline)
	{
//		//$( "#chart_div" ).hide();
//		$('#chart_status').val('hide');
//	    $('#chart_status').change();
		closeChart();
	}
	
	polyline.setMap(null);
}

// opens an infowindow to allow user to edit node information
function editNode(marker)
{
	var checkedString = marker === sourceMarker ? 'checked' : '';
	infoWindow.setContent(
			'<span class="infoLabel">Name: </span>'+
			'<input type="text" id="markerName" class="infoClass" onkeyup="saveNodeNameText(event)"/>'+
			'<br><span class="infoLabel">Node ID: </span>'+
			'<input type="number" class="infoClass" min=1 id="nodeID" />'+
			'<br><span class="infoLabel">Latitude: </span>'+
			'<input type="number" class="infoClass" min="-90" max="90" id="latitude" />' +
			'<br><span class="infoLabel">Longitude: </span>'+
			'<input type="number" class="infoClass" min="-180" max="180" id="longitude" />' +
			'<br><span class="infoLabel">Elevation:</span>'+
			'<span class="infoLabel" style="text-align:left">'+ marker.elevation.toFixed(2) + 'm </span>'+
			'<br><span class="infoLabel">Source:</span>' +
			'<input type="checkbox" id="issource" ' + checkedString +'>' +
			'<br><span class="infoLabel">Is ESR:</span>' +
			'<input type="checkbox" id="isesr" >' +
			'<br><input type="button" onclick="saveNodeInfo()" value="Save"/>'+
			'<span id="errorMsg" class="errorText"></span>'
			);
	
	
	
	infoWindow.open(map, marker);
	
	$( "#nodeID" ).change(function() {
		$( this ).css("background-color","");
		$("#errorMsg").text("");
		});
	
    $('#markerName').val(marker.getTitle());
    $('#markerName').select();
    $('#nodeID').val(marker['nodeID']);
    $('#latitude').val(roundToFour(marker.getPosition().lat()));
    $('#longitude').val(roundToFour(marker.getPosition().lng()));
    $('#isesr').prop('checked',marker.isesr);
	editNodeMarker = marker;
}

// round to 4 digits
function roundToFour(num) {    
    return +(Math.round(num + "e+4")  + "e-4");
}

// allows node edit infowindow to be accepted on an enter key press and exited on an esc key press
function saveNodeNameText(e)
{
	var code = (e.keyCode ? e.keyCode : e.which);
	//enter key was pressed
	if(code == 13)
		saveNodeInfo();
	else if(code == 27) // esc key was pressed
		infoWindow.close();
}

// save the node name and position
function saveNodeInfo()
{
	var id = parseInt($('#nodeID').val());
	if(editNodeMarker['nodeID']!==id && alreadyExistsID(id,nodes,'nodeID'))
	{
		$('#errorMsg').text("Node ID "+ id + " already exists.");
		$('#nodeID').css("background-color","#FF4D4D");
		$('#nodeID').select();
		return;
	}
	
	
	var name = $('#markerName').val();
	editNodeMarker.setTitle(name);
	editNodeMarker.set('labelContent', name);
	
	
	editNodeMarker.set('nodeID', id);
	
	var position = new google.maps.LatLng($('#latitude').val(),$('#longitude').val());
	editNodeMarker.setPosition(position);
	updateMarkerPipes(editNodeMarker);
	
	var checked = $('#issource')[0].checked;
	var isesr = $('#isesr')[0].checked;
	editNodeMarker.isesr = isesr;
	
	if(checked){
		setSource(editNodeMarker);
		editNodeMarker.isesr = false;
	}
	else if(isesr){
		editNodeMarker.setIcon(esrIcon);
		if(sourceMarker===editNodeMarker) {		
			sourceMarker = null;
		}
	}
	else{
		editNodeMarker.setIcon(markerIcon);
		if(sourceMarker===editNodeMarker) {		
			sourceMarker = null;
		}
	}
	
	infoWindow.close();
}

function setSource(marker){
	if(sourceMarker) {
		sourceMarker.setIcon(markerIcon);
	}
	sourceMarker = marker;
	sourceMarker.setIcon(sourceIcon);
	map.setCenter(sourceMarker.getPosition());
	mapCenter = sourceMarker.getPosition();
}

function displayMap () {
	map.setCenter(mapCenter);
	if(nodes.length > 0) {
		var bounds = new google.maps.LatLngBounds();
		var i;
		for(i=0;i<nodes.length;i++) {
		 bounds.extend(nodes[i].getPosition());
		}
		map.fitBounds(bounds);
	}	
}

// 	function computeTotalDistance(result) {
// 		  var total = 0;
// 		  var myroute = result.routes[0];
// 		  var i;
// 		  for (i = 0; i < myroute.legs.length; i++) {
// 		    total += myroute.legs[i].distance.value;
// 		  }
// 		  total = total / 1000;
// 		  document.getElementById('total').innerHTML = total + ' km';
// 	}

// add the given path from origin to destination
function addPath(path, origin, destination, click)	{
	if(click === undefined) {
		click = true;
	}
	
	var midicon = {
	        path: google.maps.SymbolPath.FORWARD_CLOSED_ARROW,
	        strokeColor: 'blue'
	    };
  
  		var endicon = {
	        path: google.maps.SymbolPath.CIRCLE
	    }; 
  
  		//if route start is different from origin add a straight line from origin to route start
		//if(path[0] != origin.getPosition())
		if(google.maps.geometry.spherical.computeDistanceBetween(path[0], origin.getPosition()) >= 5)
		{	
			path.splice(0,0,origin.getPosition());	
		}
		//if route end is different from destination add a straight line from route end to destination
		//if(path[path.length-1] != destination.getPosition())
		if(google.maps.geometry.spherical.computeDistanceBetween(path[path.length-1], destination.getPosition()) >= 5)
		{
			path[path.length] = destination.getPosition();	
		}
		var polyline = new google.maps.Polyline({
			path: path,
			strokeColor: 'purple',
			map: map,
			strokeWeight: 2,
			icons: [{icon: midicon,offset: '90%'},
			        {icon: endicon,offset: '0%'},
			        {icon: endicon,offset: '100%'}]
      	});
  		//string that captures the node info
		encodeString = google.maps.geometry.encoding.encodePath(polyline.getPath());
		//console.log(encodeString);
  		polyline.origin = origin;
		polyline.destination = destination;
		polyline.pipeID = getPipeID();
		//console.log(polyline.pipeID);
			
		polyline.getLength = function () {
			return google.maps.geometry.spherical.computeLength(this.getPath());
		}
		
		origin.outPipes.push(polyline);
		destination.inPipes.push(polyline);
  
			//highlight pipe on mouseover
		polyline.addListener('mouseover', function(e) {
			this.setOptions({strokeWeight: 4});
	  
//	    	  if(clickedPipe == this)
// 		  {
//	    		  var pos = e.latLng;
//	    		  var rowno = -1;
//	    		  var minDistance = Number.MAX_VALUE;
		  
//	    		  for(var i = 0; i < elevations.length; i++)
// 			  {
//	    			var dist = google.maps.geometry.spherical.computeDistanceBetween(elevations[i].location, pos);
// 			  	if(dist < minDistance)
// 			  	{
// 			  		minDistance = dist;
// 			  		rowno = i;
// 			  	}
// 			  }
//	    		  console.log(rowno);
//	    		  if (mousemarker == null) {
// 		        mousemarker = new google.maps.Marker({
// 		          position: pos,
// 		          map: map,
// 		          icon: "http://maps.google.com/mapfiles/ms/icons/green-dot.png"
// 		        });
// 		      } else {
// 		        mousemarker.setPosition(pos);
//		      	    }
		  
//	    		  if(rowno!=-1)
// 			  {
//		    		  chart.setSelection([{row:rowno,column:1}]);
// 			  }
// 		  }
		});
  		      		      	      
		polyline.addListener('click', function(event) {		    	  
			if(delPipe){
				delPipe = false;
				deletePipe(this);
			}
//	    	  else if(editPipe)
// 		  {
// 		  	editPipe = false;
// 		  	this.setEditable(true);
// 		  	editPolyLine = this;
// 		  	infoWindow.setContent('<input type="button" onclick="pipeEditDone()" value ="Done Editing"/>'
// 					);
// 			infoWindow.open(map, this);	    	        
// 		  }
			else if(splitPipe){
				splitPipe = false;
				var position = event.latLng;
				var newMarker = addMarker(position);
				addRoute(this.origin, newMarker);
				addRoute(newMarker, this.destination);
				deletePipe(this);
			}
			else{
				clickPipe(this);
				plotElevation(this);
			}
		});
		
		polyline.addListener('mouseout', function() {
			if(this!=clickedPipe){
				this.setOptions({strokeWeight: 2});
			}	
		});
  
  		polyline.addListener('rightclick', function(mouseEvent) {
	  		contextNodeMenu.hide();
	  		contextMapMenu.hide();
	  		contextPipeMenu.show(mouseEvent.latLng);
	  		rightClickPolyline = this;
		});
  
		//console.log(polyline);  
		pipes.push(polyline);
		if(click){
			clickPipe(polyline);
		}
		updateElevation(polyline);
}

function updateElevation(polyline)
{
	elevationService.getElevationAlongPath({
  		path: polyline.getPath().getArray(),
  		samples: SAMPLES
	}, function(results,status) {
		if(status==google.maps.ElevationStatus.OVER_QUERY_LIMIT)
    	{
			delay = 2 * delay;
			console.log('New delay value: '+delay+'ms.');
			setTimeout(updateElevation,delay,polyline);
			return;
    	}
		if(results===null)
			alert(status);
		delay = 100;
		polyline.elevationResults = results;
		//clickPipe(polyline);
		if(clickedPipe!==null && polyline===clickedPipe)
			plotElevation(polyline);
	   }
	);
}

// get smallest integer id that does not belong to array
function getNewID(array,field)
{
	var notAllowedIDs = [];
	
	$.each(array, function(index,value){
		notAllowedIDs.push(value[field]);
	});
	
	var newID = 1;
	var index;
	while(true)
	{
			index = $.inArray(newID, notAllowedIDs);
			if(index==-1)
			{
				break;
			}
			else
			{
				newID++;
			}
	}
	return newID;
}

// check if id already exists in array[i][field] for all i
function alreadyExistsID(id, array, field)
{
	var notAllowedIDs = [];
	
	$.each(array, function(index,value){
		notAllowedIDs.push(value[field]);
	});
	
	//console.log(typeof(notAllowedIDs[0]));
	//console.log(typeof(id));
	
	var index = $.inArray(id, notAllowedIDs);
	if(index==-1)
	{
		return false;
	}
	else
	{
		return true;
	}
}

// get a new unique node id
function getNodeID()
{
	return getNewID(nodes,'nodeID');
}

// get a new unique pipe id
function getPipeID()
{
	return getNewID(pipes,'pipeID');
}


// add a straight line path from origin to destination
function addDirectPath(origin, destination){
	var direct_path = [origin.getPosition(), destination.getPosition()];  		
	addPath(direct_path, origin, destination);
}

// add a polyline from origin marker to destination marker
function addRoute(origin, destination, addDirectPipe) {
	
	if(addDirectPipe === undefined) {
		addDirectPipe = false;
	}
	
	if(addDirectPipe){
		addDirectPath(origin, destination);
	}
	else{
		
		directionsService.route({
			origin: origin.getPosition(),
		    destination: destination.getPosition(),
		    travelMode: google.maps.TravelMode.WALKING
		  }, function(response, status) {
				if (status === google.maps.DirectionsStatus.OK) {
		      		//display.setDirections(response);
		      		var routes = response.routes;
		      		if(routes > 1) alert ('more than 1 route');
		      		var route = routes[0];
		      		var overview_path = route.overview_path;
		      
		      		addPath(overview_path, origin, destination);
				} else if (status === google.maps.DirectionsStatus.ZERO_RESULTS){
		      		addDirectPath(origin, destination);
				}
				else {
					alert('Could not get route due to: ' + status);
				}
					
		});		
	}
}

// 	function pipeEditDone()
// 	{
// 		elevationService.getElevationAlongPath({
// 	          path: editPolyLine.getPath().getArray(),
// 	          samples: SAMPLES
// 	        }, function(results) {
// 	        	editPolyLine.elevationResults = results;
// 	        	clickPipe(editPolyLine);
// 	        	plotElevation(editPolyLine);
// 	        	editPolyLine = null;
// 	        	});
// 		editPolyLine.setEditable(false);
// 		infoWindow.close();
// 		$(".context_menu_item:contains('End editing the pipe')").text('Edit the pipe');
// 		$(".context_menu_item:contains('End editing')").text('Edit a pipe');
// 	}

// change the pipe apperance on clicking it 
function clickPipe(pipe)
{
	if(clickedPipe !== null){
		clickedPipe.setOptions({strokeWeight: 2});
	}
	
	clickedPipe = pipe;
	clickedPipe.setOptions({strokeWeight: 4});
}

// plot elevation along a polyline on the chart
function plotElevation(polyline) {
    var results = polyline.elevationResults;
	elevations = results;
    var i;
    //var totalDistance = 0;
    //for (i = 0; i < elevations.length-1; i++) {
    //  totalDistance = totalDistance + google.maps.geometry.spherical.computeDistanceBetween(elevations[i].location,elevations[i+1].location);
    //}
    
    var totalDistance2 = 0;
    var polyPath = polyline.getPath().getArray();
    //console.log(polyPath.length);
    for (i = 0; i < polyPath.length-1; i++) {
      totalDistance2 = totalDistance2 + google.maps.geometry.spherical.computeDistanceBetween(polyPath[i],polyPath[i+1]);
    }
    
    //console.log(google.maps.geometry.spherical.computeLength(polyline.getPath()));
    
    //console.log(totalDistance2);
    
    data = new google.visualization.DataTable();
    data.addColumn('number', 'Distance');
    data.addColumn('number', 'Elevation');
    
    var j;
    var totalDistance = 0;
    //var old_loc = results[0].location;
    //var new_loc;
    var old_ele = results[0].elevation;
    var new_ele;
    var dist_unit = totalDistance2/((results.length-1)*chartPointMultiplier);
    var ele_unit;
    data.addRow([0, old_ele]);
    for (i = 1; i < results.length; i++) {
      	//new_loc = results[i].location;
      	new_ele = results[i].elevation;
    	//totalDistance = totalDistance + google.maps.geometry.spherical.computeDistanceBetween(old_loc,new_loc);
    	
      	//totalDistance = i*dist_unit;
    	ele_unit = (new_ele - old_ele)/chartPointMultiplier;
    	for (j=1; j <= chartPointMultiplier; j++){
    		totalDistance = (chartPointMultiplier*(i-1) + j)*dist_unit;
    		data.addRow([totalDistance, old_ele + ele_unit*j]);
    	}   	
    	//old_loc = new_loc;
    	old_ele = new_ele;
    }
    //console.log(totalDistance);
    
    var formatter = new google.visualization.NumberFormat({
        fractionDigits: 0,
        suffix: 'm',
        prefix: 'Distance: '
    });

    var formatter2 = new google.visualization.NumberFormat({
        fractionDigits: 0,
        suffix: 'm'
    });
    
    formatter.format(data, 0); // Apply formatter to first column.	    
    formatter2.format(data, 1); // Apply formatter to second column.

    
    //document.getElementById('chart_div').style.display = 'block';
    
    var chartOptions = {
  	      width: 512,
	      height: 200,
	      legend: 'none',
	      titleY: 'Elevation (m)',
	      title: polyline.origin.labelContent + ' to ' + polyline.destination.labelContent,
	      focusBorderColor: '#00ff00'
	    };
    
    chartOptions2 = {
	  	      //width: 512,
		      //height: 200,
	    	  bar: {
	    		  groupWidth: '100%'
	    	  },
		      legend: 'none',
		      vAxis: {
		          title: 'Elevation (m)'
		        },
		      hAxis: {
			        title: 'Distance (m)'
			    },
		      animation: {
		      		duration: 1000,
		            easing: 'inAndOut',
		            startup: true
		      },
		      tooltip: {
		    	  textStyle: {bold:true},
		    	  ignoreBounds: true,
		    	  isHtml: true
		      },
		      title: polyline.origin.labelContent + ' to ' + polyline.destination.labelContent
		    };
    
    $('#chart_status').val('show');
    $('#chart_status').change();
    chart.draw(data, chartOptions2);
  }

//function to redraw elevation chart
function redrawChart()
{
	chart.draw(data, chartOptions2);
}

// clear mousemarker
function clearMouseMarker() {
    if (mousemarker !== null) {
      mousemarker.setMap(null);
      mousemarker = null;
    }
  }


//get JSON string representation of nodes
function getNodesJSON()
{
	var jsonString = '[';
	var i;
	var delimiter='';
	var node;
	for(i=0;i<nodes.length;i++)
	{
		node = nodes[i];
		jsonString = jsonString + delimiter 
						+ '{"nodeid":"' + node.nodeID
						+ '","nodename":"' + node.labelContent
						+ '","latitude":"' + node.getPosition().lat()
						+ '","longitude":"' + node.getPosition().lng()
						+ '","isesr":"' + node.isesr
						+ '"}';
		delimiter = ',';				
	}
	jsonString = jsonString + ']';
	//console.log(jsonString);
	testString = jsonString;
	return jsonString;
}

//get JSON string representation of pipes
function getPipesJSON()
{
	var jsonString = '[';
	var i;
	var delimiter='';
	var pipe;
	var encodedPath;
	var alist = [];
	var a;
	for(i=0;i<pipes.length;i++)
	{
		pipe = pipes[i];
		encodedPath = google.maps.geometry.encoding.encodePath(pipe.getPath());
		jsonString = jsonString + delimiter 
						+ '{"encodedpath":"' + encodedPath
						+ '","originid":"' + pipe.origin.nodeID
						+ '","destinationid":"' + pipe.destination.nodeID
						+ '","length":"' + pipe.getLength()
						+ '"}';
		delimiter = ',';
		
		a = {};
		a.encodedpath = encodedPath;
		a.originid = pipe.origin.nodeID;
		a.destinationid = pipe.destination.nodeID;
		a.length = pipe.getLength();
		alist.push(a);
	}
	jsonString = jsonString + ']';
	//console.log(jsonString);
	
	var jsonString2 = JSON.stringify(alist);
	//console.log(jsonString2);
	testString2 = jsonString2;
	return jsonString2;
}

//load nodes from JSON string representation
function loadNodesFromJSON(jsonString)
{
	var jsonNodes = JSON.parse(jsonString);
	console.log(jsonNodes);
	
	var i;
	var jsonNode;
	for(i=0;i<jsonNodes.length;i++)
	{
		jsonNode = jsonNodes[i];
		var position = new google.maps.LatLng(jsonNode.latitude,jsonNode.longitude);
		addMarker(position, false, jsonNode.nodeid, jsonNode.nodename);
	}
}

//load pipes from JSON string representation
function loadPipesFromJSON(jsonString)
{
	var jsonPipes = JSON.parse(jsonString);
	console.log(jsonPipes);
	
	var i;
	var jsonPipe;
	for(i=0;i<jsonPipes.length;i++)
	{
		jsonPipe = jsonPipes[i];
		var path = google.maps.geometry.encoding.decodePath(jsonPipe.encodedpath);
		var origin = findNode(jsonPipe.originid);
		var destination = findNode(jsonPipe.destinationid);
		var length = jsonPipe.length;
		addPath(path, origin, destination);
	}
}

// finds the node, given the nodeid
function findNode(nodeid)
{
	var i;
	var node;
	for(i=0;i<nodes.length;i++)
	{
		node = nodes[i];
		if(nodeid==node.nodeID)
			return node;
	}
	return null;
}

function test()
{
	loadNodesFromJSON(testString);
	loadPipesFromJSON(testString2);
}