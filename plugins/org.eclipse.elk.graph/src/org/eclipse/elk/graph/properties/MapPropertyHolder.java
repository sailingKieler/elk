/*******************************************************************************
 * Copyright (c) 2009, 2017 Kiel University and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Kiel University - initial API and implementation
 *******************************************************************************/
package org.eclipse.elk.graph.properties;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * An implementation of {@link IPropertyHolder} based on a {@link HashMap}.
 *
 * @kieler.design 2011-01-17 reviewed by haf, cmot, soh
 * @kieler.rating proposed yellow 2012-07-10 msp
 * @author msp
 */
public class MapPropertyHolder implements IPropertyHolder, Serializable {

    /** the serial version UID. */
    private static final long serialVersionUID = 4507851447415709893L;
    
    /** map of property identifiers to their values. */
    private HashMap<IProperty<?>, Object> propertyMap;
    
    /**
     * {@inheritDoc}
     */
    @Override
    public <T> MapPropertyHolder setProperty(final IProperty<? super T> property, final T value) {
        if (propertyMap == null) {
            propertyMap = new HashMap<IProperty<?>, Object>();
        }
        if (value == null) {
            propertyMap.remove(property);
        } else {
            propertyMap.put(property, value);
        }
        
        return this;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T getProperty(final IProperty<T> property) {
        if (propertyMap != null) {
            @SuppressWarnings("unchecked")
            T value = (T) propertyMap.get(property);
            if (value != null) {
                return value;
            }
        }

        // the reason for the side effect below is that if a default value has been returned 
        // and the object is altered by the user, the user expects the altered object to be 
        // the value of the property in case he asks for the property again
        
        // Retrieve the default value and memorize it for our property
        T defaultValue = property.getDefault();
        if (defaultValue instanceof Cloneable) {
            setProperty(property, defaultValue);
        }
        return defaultValue;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasProperty(final IProperty<?> property) {
        return propertyMap != null && propertyMap.containsKey(property);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public MapPropertyHolder copyProperties(final IPropertyHolder other) {
        if (other == null) {
            return this;
        }

        final Map<IProperty<?>, Object> otherMap = other.getAllProperties();
        if (!otherMap.isEmpty()) {
            if (this.propertyMap == null) {
                propertyMap = new HashMap<IProperty<?>, Object>(otherMap);
            } else {
                this.propertyMap.putAll(otherMap);
            }
        }

        return this;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public Map<IProperty<?>, Object> getAllProperties() {
        if (propertyMap == null) {
            return Collections.emptyMap();
        } else {
            return propertyMap;
        }
    }
    
}
