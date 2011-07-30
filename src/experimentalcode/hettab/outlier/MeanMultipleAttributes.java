package experimentalcode.hettab.outlier;

import java.util.List;

import de.lmu.ifi.dbs.elki.algorithm.outlier.spatial.neighborhood.NeighborSetPredicate;
import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.data.type.TypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreFactory;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreUtil;
import de.lmu.ifi.dbs.elki.database.datastore.WritableDataStore;
import de.lmu.ifi.dbs.elki.database.ids.ArrayDBIDs;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.DBIDs;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.math.DoubleMinMax;
import de.lmu.ifi.dbs.elki.math.linearalgebra.Matrix;
import de.lmu.ifi.dbs.elki.database.relation.MaterializedRelation;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierResult;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierScoreMeta;
import de.lmu.ifi.dbs.elki.result.outlier.QuotientOutlierScoreMeta;
import de.lmu.ifi.dbs.elki.utilities.DatabaseUtil;
import de.lmu.ifi.dbs.elki.utilities.documentation.Description;
import de.lmu.ifi.dbs.elki.utilities.documentation.Reference;
import de.lmu.ifi.dbs.elki.utilities.documentation.Title;

/**
 * Mean Approach is used to discover spatial outliers with multiple attributes. <br>
 * Reference:<br>
 * Chang-Tien Lu and Dechang Chen and Yufeng Kou,<br>
 * Detecting Spatial Outliers with Multiple Attributes<br>
 * in 15th IEEE International Conference on Tools with Artificial Intelligence,
 * 2003
 * 
 * @author Ahmed Hettab
 * 
 * @param <N> Spatial Vector
 * @param <O> non Spatial Vector
 */
@Title("Detecting Spatial Outliers with Multiple Attributes")
@Description("Mean Approach is used to discover spatial outliers with multiple attributes.")
@Reference(authors = "Chang-Tien Lu and Dechang Chen and Yufeng Kou", title = "Detecting Spatial Outliers with Multiple Attributes", booktitle = "Proc. 15th IEEE International Conference on Tools with Artificial Intelligence, 2003")
public class MeanMultipleAttributes<N, O extends NumberVector<O, ?>> extends MultipleAttributesSpatialOutlier<N, O> {
  /**
   * logger
   */
  public static final Logging logger = Logging.getLogger(MeanMultipleAttributes.class);

  /**
   * parameter z
   */
  protected List<Integer> z;

  /**
   * Constructor
   * 
   * @param npredf
   * @param dims
   */
  public MeanMultipleAttributes(NeighborSetPredicate.Factory<N> npredf, List<Integer> z) {
    super(npredf, z);
  }

  @Override
  protected Logging getLogger() {
    return logger;
  }

  public OutlierResult run(Database database, Relation<N> spatial, Relation<O> relation) {
    final NeighborSetPredicate npred = getNeighborSetPredicateFactory().instantiate(spatial);
    Matrix hMatrix = new Matrix(getDimsOfNonSpatialAttributes().size(), relation.size());
    Matrix hMeansMatrix = new Matrix(getDimsOfNonSpatialAttributes().size(), 1);
    ArrayDBIDs ids = DBIDUtil.ensureArray(relation.getDBIDs());
    int i = 0;
    for(Integer dim : getDimsOfNonSpatialAttributes()) {

      // h mean for each dim
      double hMeans = 0;
      for(int j = 0; j < ids.size(); j++) {
        // f value
        DBID id = ids.get(j);
        double f = relation.get(id).doubleValue(dim);
        DBIDs neighbors = npred.getNeighborDBIDs(id);
        double nSize = neighbors.size();
        if(neighbors.contains(id)) {
          nSize = neighbors.size() - 1;
        }
        double g = 0;
        double h;
        if(nSize == 0) {
          h = 0;
        }
        else {
          for(DBID n : neighbors) {
            if(n.equals(id)) {
              continue;
            }
            g += relation.get(n).doubleValue(dim) / nSize;
          }
          h = Math.abs(f - g);
        }
        // add to h Matrix
        hMatrix.set(i, j, h);
        hMeans += h;
        j++;
      }

      hMeans = hMeans / relation.size();
      // add mean to h means hMeansMatrix
      hMeansMatrix.set(i, 0, hMeans);
      i++;
    }

    Matrix sigma = DatabaseUtil.covarianceMatrix(hMatrix);
    Matrix invSigma = sigma.inverse();

    DoubleMinMax minmax = new DoubleMinMax();
    WritableDataStore<Double> scores = DataStoreUtil.makeStorage(relation.getDBIDs(), DataStoreFactory.HINT_STATIC, Double.class);
    i = 0;
    for(DBID id : relation.iterDBIDs()) {
      Matrix h_i = hMatrix.getColumn(i).minus(hMeansMatrix);
      Matrix h_iT = h_i.transpose();
      Matrix m = h_iT.times(invSigma);
      Matrix sM = m.times(h_i);
      double score = sM.get(0, 0);
      minmax.put(score);
      scores.put(id, score);
      i++;
    }

    Relation<Double> scoreResult = new MaterializedRelation<Double>("mean multiple attributes spatial outlier", "mean-multipleattributes-outlier", TypeUtil.DOUBLE, scores, relation.getDBIDs());
    OutlierScoreMeta scoreMeta = new QuotientOutlierScoreMeta(minmax.getMin(), minmax.getMax(), 0.0, Double.POSITIVE_INFINITY, 0);
    return new OutlierResult(scoreMeta, scoreResult);
  }

  @Override
  public TypeInformation[] getInputTypeRestriction() {
    return TypeUtil.array(getNeighborSetPredicateFactory().getInputTypeRestriction(), TypeUtil.NUMBER_VECTOR_FIELD);
  }

  /**
   * Parameterization class.
   * 
   * @author Ahmed Hettab
   * 
   * @apiviz.exclude
   * 
   * @param <N> Neighborhood type
   * @param <O> Data Object type
   * 
   */
  public static class Parameterizer<N, O extends NumberVector<O, ?>> extends MultipleAttributesSpatialOutlier.Parameterizer<N, O> {
    @Override
    protected MeanMultipleAttributes<N, O> makeInstance() {
      return new MeanMultipleAttributes<N, O>(npredf, z);
    }
  }
}