package playground.mzilske.prognose2025;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.geotools.feature.Feature;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.ScenarioImpl;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.network.MatsimNetworkReader;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.core.utils.io.tabularFileParser.TabularFileHandler;
import org.matsim.core.utils.io.tabularFileParser.TabularFileParser;
import org.matsim.core.utils.io.tabularFileParser.TabularFileParserConfig;

import playground.mzilske.pipeline.PopulationWriterTask;
import playground.mzilske.pipeline.RoutePersonTask;



public class PVMatrixReader {

	private static final String PV_MATRIX = "../../prognose_2025/orig/pv-matrizen/2004_nuts_102r6x6.csv";

	private static final String NODES = "../../prognose_2025/orig/netze/netz-2004/strasse/knoten_wgs84.csv";

	private static final String LANDKREISE = "../../prognose_2025/osm_zellen/landkreise.shp";

	private final Set<Integer> seenCellsInMatrix = new HashSet<Integer>();

	private final Set<Integer> seenCellsInShape = new HashSet<Integer>();

	private final Set<Integer> seenCellsInNodes = new HashSet<Integer>();

	private PopulationGenerator populationBuilder = new PopulationGenerator();

	private static final String FILENAME = "../../prognose_2025/demand/population_pv_01pct.xml";

	private static final String NETWORK_FILENAME = "/Users/michaelzilske/osm/motorway_germany.xml";

	private CoordinateTransformation wgs84ToDhdnGk4 = TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, TransformationFactory.DHDN_GK4);

	private Network network; 

	public void run() {
		readNodes();
		// readShapes();
		readMatrix();
		readNetwork();
		PopulationWriterTask populationWriter = new PopulationWriterTask(FILENAME);
		populationWriter.setNetwork(network);
		Config config = new Config();
		config.addCoreModules();
		RoutePersonTask router = new RoutePersonTask(config, network);
		populationBuilder.setPopulationScaleFactor(0.01);
		populationBuilder.setSink(router);
		router.setSink(populationWriter);
		populationBuilder.run();
	}

	private void readNetwork() {
		Scenario scenario = new ScenarioImpl();
		new MatsimNetworkReader(scenario).readFile(NETWORK_FILENAME);
		network = scenario.getNetwork();
	}

	private void readShapes() {
		try {
			Collection<Feature> landkreise = new ShapeFileReader().readFileAndInitialize(LANDKREISE);
			for (Feature landkreis : landkreise) {
				Integer gemeindeschluessel = Integer.parseInt((String) landkreis.getAttribute("gemeindesc"));
				seenCellsInShape.add(gemeindeschluessel);
				populationBuilder.addShape(gemeindeschluessel, landkreis.getDefaultGeometry());
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void readNodes() {
		TabularFileParserConfig tabFileParserConfig = new TabularFileParserConfig();
		tabFileParserConfig.setFileName(NODES);
		tabFileParserConfig.setDelimiterTags(new String[] {";"});
		try {
			new TabularFileParser().parse(tabFileParserConfig,
					new TabularFileHandler() {

				@Override
				public void startRow(String[] row) {
					if (row[0].startsWith("Knoten")) {
						return;
					}
					int zone = Integer.parseInt(row[5]);
					if (!seenCellsInNodes.contains(zone)) {
						double x = Double.parseDouble(row[2]);
						double y = Double.parseDouble(row[3]);
						populationBuilder.addNode(zone, 1, 1, wgs84ToDhdnGk4.transform(new CoordImpl(x,y)));
					}
					seenCellsInNodes.add(zone);
				}

			});
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void readMatrix() {
		TabularFileParserConfig tabFileParserConfig = new TabularFileParserConfig();
		tabFileParserConfig.setFileName(PV_MATRIX);
		tabFileParserConfig.setDelimiterTags(new String[] {";"});
		try {
			new TabularFileParser().parse(tabFileParserConfig,
					new TabularFileHandler() {

				@Override
				public void startRow(String[] row) {
					if (row[0].startsWith("#")) {
						return;
					}
					int quelle = Integer.parseInt(row[0]);
					int ziel = Integer.parseInt(row[1]);
					seenCellsInMatrix.add(quelle);
					seenCellsInMatrix.add(ziel);
					if (seenCellsInNodes.contains(quelle) && seenCellsInNodes.contains(ziel)) {
						int workPt = Integer.parseInt(row[2]);
						int educationPt = Integer.parseInt(row[3]);
						int workCar = Integer.parseInt(row[8]);
						int educationCar = Integer.parseInt(row[9]);
						populationBuilder.addEntry(quelle, ziel, workCar + educationCar, workPt + educationPt);
					} else {
						System.out.println("Strange: " + quelle + " or " + ziel + " not found.");
					}
				}

			});
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void main(String[] args) {
		PVMatrixReader pvMatrixReader = new PVMatrixReader();
		pvMatrixReader.run();
	}

}
