package experimentalcode.students.roedler.parallelCoordinates.visualizer.selection;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2011
 Ludwig-Maximilians-Universität München
 Lehr- und Forschungseinheit für Datenbanksysteme
 ELKI Development Team

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.util.ArrayList;
import java.util.Iterator;

import org.apache.batik.util.SVGConstants;
import org.w3c.dom.Element;

import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreEvent;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreListener;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.database.ids.DBIDs;
import de.lmu.ifi.dbs.elki.math.linearalgebra.Vector;
import de.lmu.ifi.dbs.elki.result.DBIDSelection;
import de.lmu.ifi.dbs.elki.result.HierarchicalResult;
import de.lmu.ifi.dbs.elki.result.Result;
import de.lmu.ifi.dbs.elki.result.ResultUtil;
import de.lmu.ifi.dbs.elki.result.SelectionResult;
import de.lmu.ifi.dbs.elki.utilities.DatabaseUtil;
import de.lmu.ifi.dbs.elki.utilities.iterator.IterableUtil;
import de.lmu.ifi.dbs.elki.visualization.VisualizationTask;
import de.lmu.ifi.dbs.elki.visualization.css.CSSClass;
import de.lmu.ifi.dbs.elki.visualization.style.StyleLibrary;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGPath;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGPlot;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGUtil;
import de.lmu.ifi.dbs.elki.visualization.visualizers.AbstractVisFactory;
import de.lmu.ifi.dbs.elki.visualization.visualizers.Visualization;
import de.lmu.ifi.dbs.elki.visualization.visualizers.events.ContextChangeListener;
import de.lmu.ifi.dbs.elki.visualization.visualizers.events.ContextChangedEvent;
import de.lmu.ifi.dbs.elki.visualization.visualizers.thumbs.ThumbnailVisualization;
import experimentalcode.students.roedler.parallelCoordinates.projector.ParallelPlotProjector;
import experimentalcode.students.roedler.parallelCoordinates.visualizer.ParallelVisualization;

/**
 * Visualizer for generating SVG-Elements
 * representing the selected objects
 * 
 * @author Robert Rödler
 * 
 * @apiviz.has SelectionResult oneway - - visualizes
 * 
 * @param <NV> Type of the NumberVector being visualized.
 */
public class SelectionLineVisualization<NV extends NumberVector<NV, ?>> extends ParallelVisualization<NV> implements DataStoreListener {
  /**
   * A short name characterizing this Visualizer.
   */
  private static final String NAME = "Selection Line";

  /**
   * CSS Class for the range marker
   */
  public static final String MARKER = "SelectionLine";

  public SelectionLineVisualization(VisualizationTask task) {
    super(task);
    addCSSClasses(svgp);
    context.addDataStoreListener(this);
    context.addResultListener(this);
    incrementalRedraw();
  }
  
  @Override
  protected void redraw() {
    DBIDSelection selContext = context.getSelection();
    if(selContext != null) {
      DBIDs selection = selContext.getSelectedIds();
      
      SVGPath path;
      Vector yPos;
      for(DBID id : selection) {
          path = new SVGPath();
          yPos = proj.projectDataToRenderSpace(rep.get(id));

          for (int i = 0; i < (DatabaseUtil.dimensionality(rep)); i++){
            if (proj.isVisible(i)){
              path.drawTo(proj.getXpos(i), yPos.get(i));
            }
          }
          Element marker = path.makeElement(svgp);
          SVGUtil.addCSSClass(marker, MARKER);
          layer.appendChild(marker);
      }
    }
  }

  /**
   * Adds the required CSS-Classes
   * 
   * @param svgp SVG-Plot
   */
  private void addCSSClasses(SVGPlot svgp) {
    final StyleLibrary style = context.getStyleLibrary();
    // Class for the cube
    if(!svgp.getCSSClassManager().contains(MARKER)) {
      CSSClass cls = new CSSClass(this, MARKER);
      cls.setStatement(SVGConstants.CSS_STROKE_VALUE, style.getColor(StyleLibrary.SELECTION));
      cls.setStatement(SVGConstants.CSS_STROKE_OPACITY_PROPERTY, style.getOpacity(StyleLibrary.SELECTION) * 1.5);
      cls.setStatement(SVGConstants.CSS_STROKE_WIDTH_PROPERTY, style.getLineWidth(StyleLibrary.PLOT) / (proj.getScale() / 1.5));
      cls.setStatement(SVGConstants.CSS_STROKE_LINECAP_PROPERTY, SVGConstants.CSS_ROUND_VALUE);
      cls.setStatement(SVGConstants.CSS_STROKE_LINEJOIN_PROPERTY, SVGConstants.CSS_ROUND_VALUE);
      cls.setStatement(SVGConstants.CSS_FILL_PROPERTY, SVGConstants.CSS_NONE_VALUE);
      svgp.addCSSClassOrLogError(cls);
    }
  }
  
  @Override
  public void contentChanged(DataStoreEvent e) {
    synchronizedRedraw();
    
  }

  /**
   * Factory for visualizers to generate an SVG-Element containing a cube as
   * marker representing the selected range for each dimension
   * 
   * @author Heidi Kolb
   * 
   * @apiviz.stereotype factory
   * 
   * @param <NV> vector type
   */
  public static class Factory<NV extends NumberVector<NV, ?>> extends AbstractVisFactory {
 
    /**
     * Constructor.
     */
    public Factory() {
      super();
    }

    @Override
    public Visualization makeVisualization(VisualizationTask task) {
      return new SelectionLineVisualization<NV>(task);
    }

    @Override
    public void processNewResult(HierarchicalResult baseResult, Result result) {
      final ArrayList<SelectionResult> selectionResults = ResultUtil.filterResults(result, SelectionResult.class);
      for(SelectionResult selres : selectionResults) {
        Iterator<ParallelPlotProjector<?>> ps = ResultUtil.filteredResults(baseResult, ParallelPlotProjector.class);
        for(ParallelPlotProjector<?> p : IterableUtil.fromIterator(ps)) {
          final VisualizationTask task = new VisualizationTask(NAME, selres, p.getRelation(), this);
          task.put(VisualizationTask.META_LEVEL, VisualizationTask.LEVEL_DATA + 10);
          baseResult.getHierarchy().add(selres, task);
          baseResult.getHierarchy().add(p, task);
        }
      }
    }

    @Override
    public Visualization makeVisualizationOrThumbnail(VisualizationTask task) {
      return new ThumbnailVisualization(this, task, ThumbnailVisualization.ON_DATA | ThumbnailVisualization.ON_SELECTION);
    }
  }
}

