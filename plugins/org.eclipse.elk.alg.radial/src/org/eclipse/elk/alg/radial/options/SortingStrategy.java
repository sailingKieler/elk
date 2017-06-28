/*******************************************************************************
 * Copyright (c) 2017 Kiel University and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Kiel University - initial API and implementation
 *******************************************************************************/
package org.eclipse.elk.alg.radial.options;

import org.eclipse.elk.alg.radial.sorting.IDSorter;
import org.eclipse.elk.alg.radial.sorting.IRadialSorter;
import org.eclipse.elk.alg.radial.sorting.PolarCoordinateSorter;
import org.eclipse.elk.graph.properties.AdvancedPropertyValue;

/**
 *
 */
public enum SortingStrategy {
    /** Do no sorting. */
    NONE,
    /** Sort by polar coordinates of parent. */
    @AdvancedPropertyValue
    POLAR_COORDINATE,
    /** Sort by given order id. */
    ID;

    /**
     * Instantiate the chosen wedge strategy.
     * 
     * @return A wedge compactor.
     */
    public IRadialSorter create() {
        switch (this) {
        case NONE:
            return null;
        case POLAR_COORDINATE:
            return new PolarCoordinateSorter();
        case ID:
            return new IDSorter();
        default:
            throw new IllegalArgumentException(
                    "No implementation is available for the layout option " + this.toString());
        }
    }
}