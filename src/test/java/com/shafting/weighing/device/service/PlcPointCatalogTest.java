package com.shafting.weighing.device.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** PLC 点位表映射测试，防止页面顺序导致中轴1、中轴2地址写反。 */
class PlcPointCatalogTest {

    @Test
    void shouldMapChannelSelectionAndRealtimeTagsFromPointTable() {
        assertThat(PlcPointCatalog.channel("TAIL_SHAFT").selection()).isEqualTo("%DB100.DBX2.0:BOOL");
        assertThat(PlcPointCatalog.channel("INTERMEDIATE_2").selection()).isEqualTo("%DB100.DBX2.1:BOOL");
        assertThat(PlcPointCatalog.channel("INTERMEDIATE_1").selection()).isEqualTo("%DB100.DBX2.2:BOOL");
        assertThat(PlcPointCatalog.channel("FLYWHEEL").force()).isEqualTo("%DB100.DBD64:REAL");
        assertThat(PlcPointCatalog.channel("MAIN_ENGINE_1").motorRpm()).isEqualTo("%DB100.DBD76:REAL");
        assertThat(PlcPointCatalog.channel("MAIN_ENGINE_2").displacement()).isEqualTo("%DB100.DBD84:REAL");
    }

    @Test
    void shouldMapControlAndConfigurationTagsFromPointTable() {
        assertThat(PlcPointCatalog.control(PlcPointCatalog.Control.MANUAL))
                .isEqualTo("%DB100.DBX0.0:BOOL");
        assertThat(PlcPointCatalog.control(PlcPointCatalog.Control.AUTO_TEST))
                .isEqualTo("%DB100.DBX1.0:BOOL");
        assertThat(PlcPointCatalog.channel("TAIL_SHAFT").standardForce())
                .isEqualTo("%DB100.DBD196:REAL");
        assertThat(PlcPointCatalog.channel("INTERMEDIATE_1").forceInterval())
                .isEqualTo("%DB100.DBW264:INT");
        assertThat(PlcPointCatalog.channel("MAIN_ENGINE_2").holdSeconds())
                .isEqualTo("%DB100.DBD344:REAL");
    }

    @Test
    void shouldMapAllDb12AlarmBitsFromPointTable() {
        assertThat(PlcAlarmCatalog.tags()).hasSize(15);
        assertThat(PlcAlarmCatalog.tags().get(0).address()).isEqualTo("%DB12.DBX0.0:BOOL");
        assertThat(PlcAlarmCatalog.tags().get(14).address()).isEqualTo("%DB12.DBX1.6:BOOL");
        assertThat(PlcAlarmCatalog.tags().get(14).message()).isEqualTo("油缸无动作");
    }
}
