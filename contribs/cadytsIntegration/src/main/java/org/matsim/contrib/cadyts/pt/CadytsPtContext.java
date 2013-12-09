/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2013 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.contrib.cadyts.pt;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.cadyts.general.CadytsBuilder;
import org.matsim.contrib.cadyts.general.CadytsConfigGroup;
import org.matsim.contrib.cadyts.general.CadytsContextI;
import org.matsim.contrib.cadyts.general.CadytsCostOffsetsXMLFileIO;
import org.matsim.contrib.cadyts.general.PlansTranslator;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.AfterMobsimEvent;
import org.matsim.core.controler.events.BeforeMobsimEvent;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.AfterMobsimListener;
import org.matsim.core.controler.listener.BeforeMobsimListener;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.controler.listener.StartupListener;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.counts.Counts;
import org.matsim.counts.MatsimCountsReader;
import org.matsim.pt.config.PtCountsConfigGroup;
import org.matsim.pt.counts.PtCountSimComparisonKMLWriter;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import cadyts.calibrators.analytical.AnalyticalCalibrator;
import cadyts.measurements.SingleLinkMeasurement.TYPE;
import cadyts.supply.SimResults;

/**
 * @author nagel
 *
 */
public class CadytsPtContext implements StartupListener, IterationEndsListener, BeforeMobsimListener, AfterMobsimListener,
CadytsContextI<TransitStopFacility> {

	private final static Logger log = Logger.getLogger(CadytsPtContext.class);

	private final static String LINKOFFSET_FILENAME = "linkCostOffsets.xml";
	private static final String FLOWANALYSIS_FILENAME = "flowAnalysis.txt";
	private static final String OCCUPANCYANALYSIS_FILENAME = "cadytsPtOccupancyAnalysis.txt";

	private AnalyticalCalibrator<TransitStopFacility> calibrator = null;
	private final SimResults<TransitStopFacility> simResults;
	private final Counts occupCounts = new Counts();
	//	private final Counts boardCounts = new Counts();
	//	private final Counts alightCounts = new Counts();
	private final CadytsPtOccupancyAnalyzer cadytsPtOccupAnalyzer;
	private PtPlanToPlanStepBasedOnEvents<TransitStopFacility> ptStep ;

	private CadytsConfigGroup cadytsConfig;

	private final Config config;

	public CadytsPtContext(final Config config, EventsManager events) {
		this.config = config;
		cadytsConfig = (CadytsConfigGroup) config.getModule(CadytsConfigGroup.GROUP_NAME);

		// === prepare the structure which extracts the measurements from the simulation:
		// since there is already some other method, we just need to write a wrapper.
		
		this.cadytsPtOccupAnalyzer = new CadytsPtOccupancyAnalyzer(cadytsConfig.getCalibratedItems(), cadytsConfig.getTimeBinSize() );
		events.addHandler(this.cadytsPtOccupAnalyzer);

		this.simResults = new SimResults<TransitStopFacility>() {
			private static final long serialVersionUID = 1L;
			@Override
			public double getSimValue(TransitStopFacility stop, int startTime_s, int endTime_s, TYPE type) {
				final int timeBinSize_s = cadytsConfig.getTimeBinSize() ;
				final double countsScaleFactor = config.ptCounts().getCountsScaleFactor() ;
				double retval = 0. ;
				switch ( type ) {
				case COUNT_VEH:
					retval = cadytsPtOccupAnalyzer.getOccupancyVolumeForStopAndTime(stop.getId(), startTime_s) * countsScaleFactor ;
					break;
				case FLOW_VEH_H:
					int multiple = timeBinSize_s / 3600 ; // e.g. "3" when timeBinSize_s = 3*3600 = 10800
					retval = cadytsPtOccupAnalyzer.getOccupancyVolumeForStopAndTime(stop.getId(), startTime_s) * countsScaleFactor / multiple ;
					break;
				default:
					throw new RuntimeException("not implemented ...") ;
				}
				return retval ;
			}
			@Override
			public String toString() {
				return cadytsPtOccupAnalyzer.toString() ;
			}
		} ;

	}

	@Override
	public void notifyStartup(StartupEvent event) {
		Scenario scenario = event.getControler().getScenario();
		EventsManager events = event.getControler().getEvents();

		// === prepare the calibrator by giving measurements to it:
		String occupancyCountsFilename = config.ptCounts().getOccupancyCountsFileName();
		new MatsimCountsReader(this.occupCounts).readFile(occupancyCountsFilename);

		// build the calibrator. This is a static method, and in consequence has no side effects
		this.calibrator = CadytsBuilder.buildCalibrator(scenario.getConfig(), this.occupCounts, new TransitStopFacilityLookUp(scenario) );

		// === find out which plan is contributing what to each measurement:
		this.ptStep = new PtPlanToPlanStepBasedOnEvents<TransitStopFacility>(scenario, cadytsConfig.getCalibratedItems());
		events.addHandler(ptStep);
	}


	@Override
	public void notifyBeforeMobsim(final BeforeMobsimEvent event) {
		this.cadytsPtOccupAnalyzer.reset(event.getIteration());
	}

	@Override
	public void notifyAfterMobsim(final AfterMobsimEvent event) {
		int it = event.getIteration();

		// Get all stations of all analyzed lines and invoke the method write to get all information of them
		Set<Id> stopIds = new HashSet<Id>();
		for (Id lineId : this.cadytsConfig.getCalibratedItems()) {
			TransitLine line = event.getControler().getScenario().getTransitSchedule().getTransitLines().get(lineId);
			for (TransitRoute route : line.getRoutes().values()) {
				for (TransitRouteStop stop : route.getStops()) {
					stopIds.add(stop.getStopFacility().getId());
				}
			}
		}
		String outFile = event.getControler().getControlerIO().getIterationFilename(it, OCCUPANCYANALYSIS_FILENAME);
		this.cadytsPtOccupAnalyzer.writeResultsForSelectedStopIds(outFile, this.occupCounts, stopIds);
	}

	@Override
	public void notifyIterationEnds(final IterationEndsEvent event) {
		if (cadytsConfig.isWriteAnalysisFile()) {
			String analysisFilepath = event.getControler().getControlerIO().getIterationFilename(event.getIteration(), FLOWANALYSIS_FILENAME);
			this.calibrator.setFlowAnalysisFile(analysisFilepath);
		}

		this.calibrator.afterNetworkLoading(this.simResults);

		// write some output
		String filename = event.getControler().getControlerIO().getIterationFilename(event.getIteration(), LINKOFFSET_FILENAME);
		try {
			new CadytsCostOffsetsXMLFileIO<TransitStopFacility>(new TransitStopFacilityLookUp(event.getControler().getScenario()))  
			.write(filename, this.calibrator.getLinkCostOffsets());
		} catch (IOException e) {
			log.error("Could not write link cost offsets!", e);
		}

		generateAndWriteCountsComparisons(event);
	}

	// ===========================================================================================================================
	// private methods & pure delegate methods only below this line

	private void generateAndWriteCountsComparisons(final IterationEndsEvent event) {
		Config config = event.getControler().getConfig();

		if ( this.cadytsConfig.getTimeBinSize()!=3600 ) {
			log.warn("generateAndWriteCountsComparisons() does not work when time bin size != 3600.  See comments in code. Skipping the comparison ..." ) ;
			return ;
			// yyyy there are some conceptual problems behind this which are not resolved:
			// () There should reasonably be two methods: one describing what cadyts _thinks_ it is comparing, and one that just
			// compares the output.  There is one methods writing simCountCompare..., and then this one here 
			// writing cadytsSimCountCompare... .  It is not clarified which one is doing which.
			// () The method that just compares the output should not rely on cadyts but compute its own observations. --
			// Unfortunately, this collides with the fact that the time bin size is part of the cadyts configuration.  This is, in the end, a 
			// consequence of the fact that the Counts format assumes hourly counts (other than cadyts, which reasonably allows the
			// specify the time span for every observation separately).
			// kai, feb'13
		}


		PtCountsConfigGroup ptCountsConfig = config.ptCounts();
		if (ptCountsConfig.getAlightCountsFileName() != null) { // yyyy this check should reasonably also be done in isActiveInThisIteration. kai,oct'10
			Controler controler = event.getControler();
			int iter = event.getIteration();

			controler.stopwatch.beginOperation("compare with pt counts");

			Network network = controler.getNetwork();
			CadytsPtCountsComparisonAlgorithm ccaOccupancy = new CadytsPtCountsComparisonAlgorithm(this.cadytsPtOccupAnalyzer, this.occupCounts,
					network, config.ptCounts().getCountsScaleFactor());

			Double distanceFilter = ptCountsConfig.getDistanceFilter();
			String distanceFilterCenterNodeId  = ptCountsConfig.getDistanceFilterCenterNode();
			if ((distanceFilter != null) && (distanceFilterCenterNodeId != null)) {
				ccaOccupancy.setDistanceFilter(distanceFilter, distanceFilterCenterNodeId);
			}

			ccaOccupancy.calculateComparison();

			String outputFormat = ptCountsConfig.getOutputFormat();
			if (outputFormat.contains("kml") || outputFormat.contains("all")) {
				OutputDirectoryHierarchy ctlIO = controler.getControlerIO();

				String filename = ctlIO.getIterationFilename(iter, "cadytsPtCountscompare.kmz");
				final CoordinateTransformation coordTransform = TransformationFactory.getCoordinateTransformation(config
						.global().getCoordinateSystem(), TransformationFactory.WGS84);
				PtCountSimComparisonKMLWriter kmlWriter = new PtCountSimComparisonKMLWriter(null,
						null, ccaOccupancy.getComparison(), coordTransform, null, null, 
						this.occupCounts);

				//					CountSimComparisonKMLWriter kmlWriter = new CountSimComparisonKMLWriter(ccaOccupancy.getComparison(), network, coordTransform) ;
				// will not work since "network" contains the wrong information.

				kmlWriter.setIterationNumber(iter);
				kmlWriter.writeFile(filename);
			}

			if (outputFormat.contains("txt") || outputFormat.contains("all")) {
				//  As far as I can tell, this file is written twice, the other times without the "cadyts" part.  kai, feb'13
				//  yyyyyy As far as I can tell, the version here is wrong as soon as the time bin is different from 3600.--?? kai, feb'13
				//  See near beginning of method.  kai, feb'13 
				OutputDirectoryHierarchy ctlIO = controler.getControlerIO();
				ccaOccupancy.write(ctlIO.getIterationFilename(iter, "cadytsSimCountCompareOccupancy.txt"));
			}

			controler.stopwatch.endOperation("compare with pt counts");
		}
	}

	public AnalyticalCalibrator<TransitStopFacility> getCalibrator() {
		return (AnalyticalCalibrator<TransitStopFacility>) calibrator;
	}

	public PlansTranslator<TransitStopFacility> getPlansTranslator() {
		return (PlansTranslator<TransitStopFacility>) ptStep;
	}

}
