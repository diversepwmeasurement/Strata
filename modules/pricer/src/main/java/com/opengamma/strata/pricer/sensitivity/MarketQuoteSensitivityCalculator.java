/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.sensitivity;

import java.util.Map;
import java.util.Map.Entry;

import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.market.curve.Curve;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.DefaultCurveMetadata;
import com.opengamma.strata.market.sensitivity.CurveCurrencyParameterSensitivities;
import com.opengamma.strata.market.sensitivity.CurveCurrencyParameterSensitivity;
import com.opengamma.strata.math.impl.matrix.DoubleMatrix1D;
import com.opengamma.strata.math.impl.matrix.DoubleMatrix2D;
import com.opengamma.strata.math.impl.matrix.MatrixAlgebra;
import com.opengamma.strata.math.impl.matrix.OGMatrixAlgebra;
import com.opengamma.strata.pricer.calibration.JacobianCurveCalibrationInfo;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;

/**
 * Calculator to obtain the Market Quote sensitivities.
 * <p>
 * This needs the {@link JacobianCurveCalibrationInfo} obtained during curve calibration.
 * The Market Quote sensitivities are also called Par Rate when the instruments used
 * in the curve calibration are quoted in rate, e.g. IRS, FRA or OIS.
 */
public class MarketQuoteSensitivityCalculator {

  /**
   * The default instance.
   */
  public static final MarketQuoteSensitivityCalculator DEFAULT = new MarketQuoteSensitivityCalculator();
  /**
   * The matrix algebra used for matrix inversion.
   */
  private static final MatrixAlgebra MATRIX_ALGEBRA = new OGMatrixAlgebra();

  //-------------------------------------------------------------------------
  /**
   * Calculates the market quote sensitivities from parameter sensitivity.  
   * 
   * @param paramSensitivities  the curve parameter sensitivities
   * @param provider  the rates provider, containing Jacobian calibration information
   * @return the market quote sensitivities
   */
  public CurveCurrencyParameterSensitivities sensitivity(
      CurveCurrencyParameterSensitivities paramSensitivities,
      ImmutableRatesProvider provider) {

    ArgChecker.notNull(paramSensitivities, "paramSensitivities");
    ArgChecker.notNull(provider, "provider");

    CurveCurrencyParameterSensitivities result = CurveCurrencyParameterSensitivities.empty();
    for (CurveCurrencyParameterSensitivity paramSens : paramSensitivities.getSensitivities()) {
      // find the matching calibration info
      Curve curve = provider.findCurve(paramSens.getCurveName())
          .orElseThrow(() -> new IllegalArgumentException(
              "Market Quote sensitivity requires curve: " + paramSens.getCurveName()));
      JacobianCurveCalibrationInfo info = curve.getMetadata().getCalibrationInfo()
          .orElseThrow(() -> new IllegalArgumentException(
              "Market Quote sensitivity requires Jacobian calibration information"))
          .convertTo(JacobianCurveCalibrationInfo.class);

      // calculate the market quote sensitivity using the Jacobian
      DoubleMatrix2D jacobian = info.getJacobianMatrix();
      DoubleMatrix1D paramSensMatrix = new DoubleMatrix1D(paramSens.getSensitivity());
      DoubleMatrix1D marketQuoteSensMatrix = (DoubleMatrix1D) MATRIX_ALGEBRA.multiply(paramSensMatrix, jacobian);
      double[] marketQuoteSens = marketQuoteSensMatrix.getData();

      // split between different curves
      Map<CurveName, double[]> split = info.splitValues(marketQuoteSens);
      for (Entry<CurveName, double[]> entry : split.entrySet()) {
        // build result without curve metadata
        CurveCurrencyParameterSensitivity maketQuoteSens = CurveCurrencyParameterSensitivity.of(
            DefaultCurveMetadata.of(entry.getKey()),
            paramSens.getCurrency(),
            entry.getValue());
        result = result.combinedWith(maketQuoteSens);
      }
    }
    return result;
  }

}
