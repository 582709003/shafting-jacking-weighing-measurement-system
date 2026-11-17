package com.shafting.weighing.curve.service;

import com.shafting.weighing.curve.dto.CurvePoint;
import com.shafting.weighing.curve.dto.CurveSegment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CurveMathTest {

    @Test
    void shouldFitTheSteepestAdjacentPairAndCalculateForceAxisIntercept() {
        List<CurvePoint> points = List.of(
                point("20", "0.02"),
                point("40", "0.05"),
                point("60", "0.15"),
                point("90", "0.20"));

        CurveSegment segment = CurveMath.fitAdjacent(points);

        assertThat(segment.first()).isEqualTo(point("40", "0.05"));
        assertThat(segment.second()).isEqualTo(point("60", "0.15"));
        assertThat(CurveMath.forceIntercept(segment)).isEqualByComparingTo("30.00");

        CurveSegment anchored = CurveMath.anchorAtMaximumDisplacement(segment, new BigDecimal("0.20"));
        assertThat(anchored.first()).isEqualTo(point("30.00", "0.00"));
        assertThat(anchored.second()).isEqualTo(point("70.00", "0.20"));
    }

    private CurvePoint point(String force, String displacement) {
        return new CurvePoint(new BigDecimal(force), new BigDecimal(displacement));
    }
}
