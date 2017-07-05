/*******************************************************************************
 * Copyright (c) 2015 Kiel University and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Kiel University - initial API and implementation
 *******************************************************************************/
package org.eclipse.elk.alg.layered;

import java.util.Random;
import java.util.Set;

import org.eclipse.elk.alg.layered.graph.LGraph;
import org.eclipse.elk.alg.layered.graph.LGraphUtil;
import org.eclipse.elk.alg.layered.intermediate.IntermediateProcessorStrategy;
import org.eclipse.elk.alg.layered.options.CrossingMinimizationStrategy;
import org.eclipse.elk.alg.layered.options.GraphCompactionStrategy;
import org.eclipse.elk.alg.layered.options.GraphProperties;
import org.eclipse.elk.alg.layered.options.GreedySwitchType;
import org.eclipse.elk.alg.layered.options.InternalProperties;
import org.eclipse.elk.alg.layered.options.LayeredOptions;
import org.eclipse.elk.alg.layered.options.NodePromotionStrategy;
import org.eclipse.elk.alg.layered.options.Spacings;
import org.eclipse.elk.alg.layered.p5edges.EdgeRouterFactory;
import org.eclipse.elk.core.alg.AlgorithmAssembler;
import org.eclipse.elk.core.alg.LayoutProcessorConfiguration;
import org.eclipse.elk.core.labels.LabelManagementOptions;
import org.eclipse.elk.core.options.Direction;
import org.eclipse.elk.core.options.EdgeRouting;

/**
 * The configurator configures a graph in preparation for layout. This includes making sure that a
 * given graph has sensible properties set as well as assembling the phases and processors required
 * to layout the graph. That list is attached to the graph in its
 * {@link InternalProperties#PROCESSORS} property.
 * 
 * <p>Each phase and processor is cached, so a given instance of this class can safely (and usually
 * should) be reused.</p>
 * 
 * @author cds
 */
final class GraphConfigurator {

    ////////////////////////////////////////////////////////////////////////////////
    // Constants
    
    /** intermediate processing configuration for basic graphs. */
    private static final LayoutProcessorConfiguration<LayeredPhases, LGraph> BASELINE_PROCESSING_CONFIGURATION =
        LayoutProcessorConfiguration.<LayeredPhases, LGraph>create()
            .addBefore(LayeredPhases.P4_NODE_PLACEMENT, IntermediateProcessorStrategy.NODE_MARGIN_CALCULATOR)
            .addBefore(LayeredPhases.P4_NODE_PLACEMENT, IntermediateProcessorStrategy.LABEL_AND_NODE_SIZE_PROCESSOR)
            .addBefore(LayeredPhases.P5_EDGE_ROUTING,
                    IntermediateProcessorStrategy.LAYER_SIZE_AND_GRAPH_HEIGHT_CALCULATOR);
    
    /** intermediate processors for label management. */
    private static final LayoutProcessorConfiguration<LayeredPhases, LGraph> LABEL_MANAGEMENT_ADDITIONS =
        LayoutProcessorConfiguration.<LayeredPhases, LGraph>create()
            .addBefore(LayeredPhases.P3_NODE_ORDERING, IntermediateProcessorStrategy.LABEL_MANAGEMENT_PROCESSOR);

    
    ////////////////////////////////////////////////////////////////////////////////
    // Processor Caching
    
    /** The algorithm assembler we use to assemble our algorithm configurations. */
    private final AlgorithmAssembler<LayeredPhases, LGraph> algorithmAssembler =
            AlgorithmAssembler.<LayeredPhases, LGraph>create(LayeredPhases.class);
    
    
    ////////////////////////////////////////////////////////////////////////////////
    // Graph Preprocessing (Property Configuration)

    /** the minimal spacing between edges, so edges won't overlap. */
    private static final double MIN_EDGE_SPACING = 2.0;

    /**
     * Set special layout options for the layered graph.
     * 
     * @param lgraph a new layered graph
     */
    private void configureGraphProperties(final LGraph lgraph) {
        // check the bounds of some layout options
        // TODO Find a new concept for checking validity of bounds
//        lgraph.checkProperties(InternalProperties.SPACING, InternalProperties.BORDER_SPACING,
//                Properties.THOROUGHNESS, InternalProperties.ASPECT_RATIO);
        
        double edgeSpacing = lgraph.getProperty(LayeredOptions.SPACING_EDGE_EDGE);
        if (edgeSpacing < MIN_EDGE_SPACING) {
            // Make sure the resulting edge spacing is at least 2 in order to avoid overlapping edges.
            lgraph.setProperty(LayeredOptions.SPACING_EDGE_EDGE, MIN_EDGE_SPACING);
        }
        
        Direction direction = lgraph.getProperty(LayeredOptions.DIRECTION);
        if (direction == Direction.UNDEFINED) {
            lgraph.setProperty(LayeredOptions.DIRECTION, LGraphUtil.getDirection(lgraph));
        }
        
        // set the random number generator based on the random seed option
        Integer randomSeed = lgraph.getProperty(LayeredOptions.RANDOM_SEED);
        if (randomSeed == 0) {
            lgraph.setProperty(InternalProperties.RANDOM, new Random());
        } else {
            lgraph.setProperty(InternalProperties.RANDOM, new Random(randomSeed));
        }
        
        Boolean favorStraightness = lgraph.getProperty(LayeredOptions.NODE_PLACEMENT_FAVOR_STRAIGHT_EDGES);
        if (favorStraightness == null) {
            lgraph.setProperty(LayeredOptions.NODE_PLACEMENT_FAVOR_STRAIGHT_EDGES,
                    lgraph.getProperty(LayeredOptions.EDGE_ROUTING) == EdgeRouting.ORTHOGONAL);
        }
        
        // pre-calculate spacing information
        Spacings spacings = new Spacings(lgraph);
        lgraph.setProperty(InternalProperties.SPACINGS, spacings);
    }
    
    
    ////////////////////////////////////////////////////////////////////////////////
    // Updating the Configuration
    
    /**
     * Rebuilds the configuration to include all processors required to layout the given graph. The list
     * of processors is attached to the graph in the {@link InternalProperties#PROCESSORS} property.
     * 
     * @param lgraph the graph to layout.
     */
    public void prepareGraphForLayout(final LGraph lgraph) {
        // Make sure the graph properties are sensible
        configureGraphProperties(lgraph);
        
        // Setup the algorithm assembler
        algorithmAssembler.reset();
        
        algorithmAssembler.setPhase(LayeredPhases.P1_CYCLE_BREAKING,
                lgraph.getProperty(LayeredOptions.CYCLE_BREAKING_STRATEGY));
        algorithmAssembler.setPhase(LayeredPhases.P2_LAYERING,
                lgraph.getProperty(LayeredOptions.LAYERING_STRATEGY));
        algorithmAssembler.setPhase(LayeredPhases.P3_NODE_ORDERING,
                lgraph.getProperty(LayeredOptions.CROSSING_MINIMIZATION_STRATEGY));
        algorithmAssembler.setPhase(LayeredPhases.P4_NODE_PLACEMENT,
                lgraph.getProperty(LayeredOptions.NODE_PLACEMENT_STRATEGY));
        algorithmAssembler.setPhase(LayeredPhases.P5_EDGE_ROUTING,
                EdgeRouterFactory.factoryFor(lgraph.getProperty(LayeredOptions.EDGE_ROUTING)));
        
        algorithmAssembler.addProcessorConfiguration(getPhaseIndependentLayoutProcessorConfiguration(lgraph));
        
        lgraph.setProperty(InternalProperties.PROCESSORS, algorithmAssembler.build(lgraph));
    }
    
    /**
     * Returns an intermediate processing configuration with processors not tied to specific phases.
     * 
     * @param lgraph the layered graph to be processed. The configuration may vary depending on certain
     *               properties of the graph.
     * @return intermediate processing configuration. May be {@code null}.
     */
    private LayoutProcessorConfiguration<LayeredPhases, LGraph> getPhaseIndependentLayoutProcessorConfiguration(
            final LGraph lgraph) {

        Set<GraphProperties> graphProperties = lgraph.getProperty(InternalProperties.GRAPH_PROPERTIES);

        // Basic configuration
        LayoutProcessorConfiguration<LayeredPhases, LGraph> configuration =
                LayoutProcessorConfiguration.createFrom(BASELINE_PROCESSING_CONFIGURATION);

        // Port side processor, put to first slot only if requested and routing is orthogonal
        if (lgraph.getProperty(LayeredOptions.FEEDBACK_EDGES)) {
            configuration.addBefore(LayeredPhases.P1_CYCLE_BREAKING, IntermediateProcessorStrategy.PORT_SIDE_PROCESSOR);
        } else {
            configuration.addBefore(LayeredPhases.P3_NODE_ORDERING, IntermediateProcessorStrategy.PORT_SIDE_PROCESSOR);
        }
        
        // If the graph has a label manager, so add label management additions
        if (lgraph.getProperty(LabelManagementOptions.LABEL_MANAGER) != null) {
            configuration.addAll(LABEL_MANAGEMENT_ADDITIONS);
        }

        // graph transformations for unusual layout directions
        switch (lgraph.getProperty(LayeredOptions.DIRECTION)) {
        case LEFT:
            configuration
                .addBefore(LayeredPhases.P1_CYCLE_BREAKING, IntermediateProcessorStrategy.LEFT_DIR_PREPROCESSOR)
                .addAfter(LayeredPhases.P5_EDGE_ROUTING, IntermediateProcessorStrategy.LEFT_DIR_POSTPROCESSOR);
            break;
        case DOWN:
            configuration
                .addBefore(LayeredPhases.P1_CYCLE_BREAKING, IntermediateProcessorStrategy.DOWN_DIR_PREPROCESSOR)
                .addAfter(LayeredPhases.P5_EDGE_ROUTING, IntermediateProcessorStrategy.DOWN_DIR_POSTPROCESSOR);
            break;
        case UP:
            configuration
                .addBefore(LayeredPhases.P1_CYCLE_BREAKING, IntermediateProcessorStrategy.UP_DIR_PREPROCESSOR)
                .addAfter(LayeredPhases.P5_EDGE_ROUTING, IntermediateProcessorStrategy.UP_DIR_POSTPROCESSOR);
            break;
        default:
            // This is either RIGHT or UNDEFINED, which is just mapped to RIGHT. Either way, we
            // don't need any processors here
            break;
        }

        // Additional dependencies
        if (graphProperties.contains(GraphProperties.COMMENTS)) {
            configuration
                .addBefore(LayeredPhases.P1_CYCLE_BREAKING, IntermediateProcessorStrategy.COMMENT_PREPROCESSOR)
                .addAfter(LayeredPhases.P5_EDGE_ROUTING, IntermediateProcessorStrategy.COMMENT_POSTPROCESSOR);
        }
        
        // Node-Promotion application for reduction of dummy nodes after layering
        if (lgraph.getProperty(LayeredOptions.LAYERING_NODE_PROMOTION_STRATEGY) != NodePromotionStrategy.NONE) {
            configuration.addBefore(LayeredPhases.P3_NODE_ORDERING, IntermediateProcessorStrategy.NODE_PROMOTION);
        }

        // Preserve certain partitions during layering
        if (graphProperties.contains(GraphProperties.PARTITIONS)) {
            configuration.addBefore(LayeredPhases.P1_CYCLE_BREAKING,
                    IntermediateProcessorStrategy.PARTITION_PREPROCESSOR);
            configuration.addBefore(LayeredPhases.P3_NODE_ORDERING,
                    IntermediateProcessorStrategy.PARTITION_POSTPROCESSOR);
        }
        
        // Additional horizontal compaction depends on orthogonal edge routing
        if (lgraph.getProperty(LayeredOptions.COMPACTION_POST_COMPACTION_STRATEGY) != GraphCompactionStrategy.NONE
              && lgraph.getProperty(LayeredOptions.EDGE_ROUTING) != EdgeRouting.POLYLINE) {
            configuration.addAfter(LayeredPhases.P5_EDGE_ROUTING, IntermediateProcessorStrategy.HORIZONTAL_COMPACTOR);
        }
        
        // Move trees of high degree nodes to separate layers
        if (lgraph.getProperty(LayeredOptions.HIGH_DEGREE_NODES_TREATMENT)) {
            configuration.addBefore(LayeredPhases.P3_NODE_ORDERING,
                    IntermediateProcessorStrategy.HIGH_DEGREE_NODE_LAYER_PROCESSOR);
        }
        
        // Introduce in-layer constraints to preserve the order of regular nodes
        if (lgraph.getProperty(LayeredOptions.CROSSING_MINIMIZATION_SEMI_INTERACTIVE)) {
            configuration.addBefore(LayeredPhases.P3_NODE_ORDERING,
                    IntermediateProcessorStrategy.SEMI_INTERACTIVE_CROSSMIN_PROCESSOR);
        }

        // Configure greedy switch, activate it if the following holds true
        //  a) no interactive crossing minimization is performed
        //  b) the greedy switch type is set to something different than OFF
        //  c) the activationThreshold is larger than or equal to the graph's number of nodes (or '0')
        GreedySwitchType greedySwitchType = lgraph.getProperty(LayeredOptions.CROSSING_MINIMIZATION_GREEDY_SWITCH_TYPE);
        boolean interactiveCrossMin =
                lgraph.getProperty(LayeredOptions.CROSSING_MINIMIZATION_SEMI_INTERACTIVE) 
                || lgraph.getProperty(
                        LayeredOptions.CROSSING_MINIMIZATION_STRATEGY) == CrossingMinimizationStrategy.INTERACTIVE;
        int activationThreshold =
                lgraph.getProperty(LayeredOptions.CROSSING_MINIMIZATION_GREEDY_SWITCH_ACTIVATION_THRESHOLD);
        int graphSize = lgraph.getLayerlessNodes().size();
        if (!interactiveCrossMin && greedySwitchType != GreedySwitchType.OFF 
                && (activationThreshold == 0 || activationThreshold > graphSize)) {
            IntermediateProcessorStrategy internalGreedyType = 
                    greedySwitchType == GreedySwitchType.ONE_SIDED
                        ? IntermediateProcessorStrategy.ONE_SIDED_GREEDY_SWITCH
                        : IntermediateProcessorStrategy.TWO_SIDED_GREEDY_SWITCH;
            configuration.addBefore(LayeredPhases.P4_NODE_PLACEMENT, internalGreedyType);
        }

        // Wrapping of graphs
        switch (lgraph.getProperty(LayeredOptions.WRAPPING_STRATEGY)) {
        case PATH_LIKE:
            configuration.addBefore(LayeredPhases.P4_NODE_PLACEMENT,
                    IntermediateProcessorStrategy.PATH_LIKE_GRAPH_WRAPPER);
            break;
        case GENERAL:
            configuration
                    .addBefore(LayeredPhases.P3_NODE_ORDERING,
                            IntermediateProcessorStrategy.BREAKING_POINT_INSERTER)
                    .addBefore(LayeredPhases.P4_NODE_PLACEMENT, IntermediateProcessorStrategy.BREAKING_POINT_PROCESSOR)
                    .addAfter(LayeredPhases.P5_EDGE_ROUTING, IntermediateProcessorStrategy.BREAKING_POINT_REMOVER);
            break;
        default: // OFF
        }
        
        return configuration;
    }
    
}
