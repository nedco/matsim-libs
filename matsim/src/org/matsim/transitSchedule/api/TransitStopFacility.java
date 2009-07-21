/* *********************************************************************** *
 * project: org.matsim.*
 * ITransitStopFacility.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
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

package org.matsim.transitSchedule.api;

import org.matsim.core.api.experimental.network.Link;
import org.matsim.core.facilities.Facility;

/**
 * A facility (infrastructure) describing a public transport stop.
 *
 * @author mrieser
 */
public interface TransitStopFacility extends Facility {

	boolean getIsBlockingLane();

	public void setLink(final Link link);
	
	/**
	 * Sets a human name for the stop facility, e.g. to be displayed 
	 * on vehicles or at the stops' locations. The name can be 
	 * <code>null</code> to delete a previously assigned name.
	 * 
	 * @param name
	 */
	public void setName(final String name);
	
	/**
	 * @return name of the stop facility. Can be <code>null</code>.
	 */
	public String getName();

}