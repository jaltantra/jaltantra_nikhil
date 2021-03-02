package structs;

//container for general network information

//name_project: name of project
//name_organization: name of organization
//min_node_pressure: default minimum pressure in metres that must be maintained at all nodes (can be overridden for individual nodes)
//def_pipe_roughness: default hazen-williams pipe roughness coefficient (can be overridden for individual pipes)
//min_hl_km: minimum headloss per kilometre, in metres per km, that must exist in each pipe
//max_hl_km: maximum headloss per kilometre, in metres per km, that must exist in each pipe
//max_water_speed: maximum water speed allowed in pipes, in metres per second
//max_pipe_pressure: maximum pressure in metres allowed in pipes
//supply_hours: number of hours in a day for which the source provides water to the network
//source_head: water head provided by the source in metres
//source_elevation: elevation of source in metres
//source_nodeid: unique integer node id of the source
//source_nodename: node name of the source 
public class GeneralStruct
{
	public String name_project;
	public String name_organization;
	public double min_node_pressure;
	public double def_pipe_roughness;
	public double min_hl_perkm;
	public double max_hl_perkm;
	public double max_water_speed;
	public double max_pipe_pressure;
	public double supply_hours;
	public double source_head;
	public double source_elevation;
	public int source_nodeid;
	public String source_nodename;
	
	public GeneralStruct(String name_project, String name_organization, double min_node_pressure,	double def_pipe_roughness, 
			double min_hl_perkm, double max_hl_perkm, double max_water_speed, double max_pipe_pressure, double supply_hours, 
			double source_head, double source_elevation, int source_nodeid,
			String source_nodename) {
		this.name_project = name_project;
		this.name_organization = name_organization;
		this.min_node_pressure = min_node_pressure;
		this.def_pipe_roughness = def_pipe_roughness;
		this.min_hl_perkm = min_hl_perkm;
		this.max_hl_perkm = max_hl_perkm;
		this.max_water_speed = max_water_speed;
		this.max_pipe_pressure = max_pipe_pressure;
		this.supply_hours = supply_hours;
		this.source_head = source_head;
		this.source_elevation = source_elevation;
		this.source_nodeid = source_nodeid;
		this.source_nodename = source_nodename;
	}

	@Override
	public String toString() {
		return "GeneralStruct [name_project=" + name_project + ", name_organization=" + name_organization + ", min_node_pressure=" + min_node_pressure
				+ ", def_pipe_roughness=" + def_pipe_roughness + ", min_hl_perkm=" + min_hl_perkm
				+ ", max_hl_perkm=" + max_hl_perkm + ", max_water_speed=" + max_water_speed + ", max_pipe_pressure=" + max_pipe_pressure + ", supply_hours=" + supply_hours
				+ ", source_head=" + source_head + ", source_elevation=" + source_elevation
				+ ", source_nodeid=" + source_nodeid + ", source_nodename=" + source_nodename + "]";
	}
}