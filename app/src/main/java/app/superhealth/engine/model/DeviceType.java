/*  Copyright (C) 2015-2026 115ek, akasaka / Genjitsu Labs, Alicia Hormann,
    Andreas Böhler, Andreas Shimokawa, Andrew Watkins, angelpup, Carsten Pfeiffer,
    Cre3per, Damien Gaignon, DanialHanif, Daniel Dakhno, Daniele Gobbetti, Daniel
    Thompson, Da Pa, Dmytro Bielik, Frank Ertl, Gabriele Monaco, GeekosaurusR3x,
    Guido Jäkel, Gordon Williams, Jean-François Greffier, jfgreffier, jhey, João Paulo
    Barraca, Jochen S, Johannes Krude, José Rebelo, ksiwczynski, ladbsoft,
    Lesur Frederic, Maciej Kuśnierz, mamucho, Manuel Ruß, Maxime Reyrolle,
    maxirnilian, Michael, narektor, Noodlez, odavo32nof, opavlov, pangwalla,
    Pavel Elagin, Petr Kadlec, Petr Vaněk, protomors, Quallenauge, Quang Ngô,
    Raghd Hamzeh, Sami Alaoui, Sebastian Kranz, sedy89, Sophanimus, Stefan Bora,
    Taavi Eomäe, thermatk, tiparega, Vadim Kaushan, x29a, xaos, Yoran Vulker,
    Yukai Li, Thomas Kuehne

    This file is part of SuperHealth.

    SuperHealth is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    SuperHealth is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package app.superhealth.engine.model;

import java.lang.reflect.Constructor;

import app.superhealth.engine.devices.DeviceCoordinator;
import app.superhealth.engine.devices.UnknownDeviceCoordinator;
import app.superhealth.engine.devices.aawireless.AAWirelessCoordinator;
import app.superhealth.engine.devices.asteroidos.AsteroidOSDeviceCoordinator;
import app.superhealth.engine.devices.atcbleoepl.ATCBLEOEPLCoordinator;
import app.superhealth.engine.devices.bandwpseries.BandWPSeriesDeviceCoordinator;
import app.superhealth.engine.devices.banglejs.BangleJSCoordinator;
import app.superhealth.engine.devices.beurer.BeurerBm59Coordinator;
import app.superhealth.engine.devices.beurer.BeurerBm69Coordinator;
import app.superhealth.engine.devices.binary_sensor.coordinator.BinarySensorCoordinator;
import app.superhealth.engine.devices.bm2.Bm2Coordinator;
import app.superhealth.engine.devices.bm6.Bm6Coordinator;
import app.superhealth.engine.devices.braun.BraunBPW4500DeviceCoordinator;
import app.superhealth.engine.devices.cardo.Ls24xDeviceCoordinator;
import app.superhealth.engine.devices.casio.ecbs100.CasioECBS100DeviceCoordinator;
import app.superhealth.engine.devices.casio.gb6900.CasioGB6900DeviceCoordinator;
import app.superhealth.engine.devices.casio.gbd200.CasioGBD200DeviceCoordinator;
import app.superhealth.engine.devices.casio.gbx100.CasioGBX100DeviceCoordinator;
import app.superhealth.engine.devices.casio.gwb5600.CasioGMWB5000DeviceCoordinator;
import app.superhealth.engine.devices.casio.gwb5600.CasioGWB5600DeviceCoordinator;
import app.superhealth.engine.devices.casio.ggb100.CasioGGB100DeviceCoordinator;
import app.superhealth.engine.devices.cmfwatchpro.CmfWatchPro2Coordinator;
import app.superhealth.engine.devices.cmfwatchpro.CmfWatchPro3Coordinator;
import app.superhealth.engine.devices.cmfwatchpro.CmfWatchProCoordinator;
import app.superhealth.engine.devices.coospo.CoospoH6Coordinator;
import app.superhealth.engine.devices.coospo.CoospoH9ZCoordinator;
import app.superhealth.engine.devices.coospo.CoospoHW807Coordinator;
import app.superhealth.engine.devices.coospo.CoospoHW9Coordinator;
import app.superhealth.engine.devices.cycling_sensor.coordinator.CyclingSensorCoordinator;
import app.superhealth.engine.devices.divoom.PixooCoordinator;
import app.superhealth.engine.devices.dji.cameras.DjiOsmoAction4Coordinator;
import app.superhealth.engine.devices.concept2.Concept2PM5Coordinator;
import app.superhealth.engine.devices.coros.CorosPace3Coordinator;
import app.superhealth.engine.devices.domyos.DomyosT540Coordinator;
import app.superhealth.engine.devices.earfun.EarFunAirPro4Coordinator;
import app.superhealth.engine.devices.earfun.EarFunAirSCoordinator;
import app.superhealth.engine.devices.earfun.EarFunFreePro3Coordinator;
import app.superhealth.engine.devices.eightbitdo.EightBitDoMicroCoordinator;
import app.superhealth.engine.devices.evenrealities.G1DeviceCoordinator;
import app.superhealth.engine.devices.femometer.FemometerVinca2DeviceCoordinator;
import app.superhealth.engine.devices.fitcloud.ak102.Ak102Coordinator;
import app.superhealth.engine.devices.fitcloud.ak75.Ak75ProCoordinator;
import app.superhealth.engine.devices.fitcloud.ak86.Ak86Coordinator;
import app.superhealth.engine.devices.fitcloud.colmi.ColmiI31Coordinator;
import app.superhealth.engine.devices.fitpro.FitProDeviceCoordinator;
import app.superhealth.engine.devices.fitpro.colacao.ColaCao21Coordinator;
import app.superhealth.engine.devices.fitpro.colacao.ColaCao23Coordinator;
import app.superhealth.engine.devices.flipper.zero.FlipperZeroCoordinator;
import app.superhealth.engine.devices.galaxy_buds.GalaxyBuds2DeviceCoordinator;
import app.superhealth.engine.devices.galaxy_buds.GalaxyBuds2ProDeviceCoordinator;
import app.superhealth.engine.devices.galaxy_buds.GalaxyBuds3ProDeviceCoordinator;
import app.superhealth.engine.devices.galaxy_buds.GalaxyBudsDeviceCoordinator;
import app.superhealth.engine.devices.galaxy_buds.GalaxyBudsLiveDeviceCoordinator;
import app.superhealth.engine.devices.galaxy_buds.GalaxyBudsProDeviceCoordinator;
import app.superhealth.engine.devices.garmin.bike.GarminEdge1000Coordinator;
import app.superhealth.engine.devices.garmin.bike.GarminEdge1030Coordinator;
import app.superhealth.engine.devices.garmin.bike.GarminEdge1030PlusCoordinator;
import app.superhealth.engine.devices.garmin.bike.GarminEdge1040Coordinator;
import app.superhealth.engine.devices.garmin.bike.GarminEdge1050Coordinator;
import app.superhealth.engine.devices.garmin.bike.GarminEdge130Coordinator;
import app.superhealth.engine.devices.garmin.bike.GarminEdge130PlusCoordinator;
import app.superhealth.engine.devices.garmin.bike.GarminEdge25Coordinator;
import app.superhealth.engine.devices.garmin.bike.GarminEdge510Coordinator;
import app.superhealth.engine.devices.garmin.bike.GarminEdge520Coordinator;
import app.superhealth.engine.devices.garmin.bike.GarminEdge520PlusCoordinator;
import app.superhealth.engine.devices.garmin.bike.GarminEdge530Coordinator;
import app.superhealth.engine.devices.garmin.bike.GarminEdge540Coordinator;
import app.superhealth.engine.devices.garmin.bike.GarminEdge550Coordinator;
import app.superhealth.engine.devices.garmin.bike.GarminEdge810Coordinator;
import app.superhealth.engine.devices.garmin.bike.GarminEdge820Coordinator;
import app.superhealth.engine.devices.garmin.bike.GarminEdge830Coordinator;
import app.superhealth.engine.devices.garmin.bike.GarminEdge840Coordinator;
import app.superhealth.engine.devices.garmin.bike.GarminEdge850Coordinator;
import app.superhealth.engine.devices.garmin.bike.GarminEdgeExplore1000Coordinator;
import app.superhealth.engine.devices.garmin.bike.GarminEdgeExplore2Coordinator;
import app.superhealth.engine.devices.garmin.bike.GarminEdgeExplore820Coordinator;
import app.superhealth.engine.devices.garmin.bike.GarminEdgeExploreCoordinator;
import app.superhealth.engine.devices.garmin.bike.GarminEdgeMtbCoordinator;
import app.superhealth.engine.devices.garmin.gps.GarminETrexSeCoordinator;
import app.superhealth.engine.devices.garmin.gps.GarminGpsmap66iCoordinator;
import app.superhealth.engine.devices.garmin.gps.GarminGpsmap66sCoordinator;
import app.superhealth.engine.devices.garmin.gps.GarminGpsmap66srCoordinator;
import app.superhealth.engine.devices.garmin.gps.GarminGpsmapH1Coordinator;
import app.superhealth.engine.devices.garmin.gps.GarminInReachMini2;
import app.superhealth.engine.devices.garmin.hrm.GarminHrm200Coordinator;
import app.superhealth.engine.devices.garmin.hrm.GarminHrm600Coordinator;
import app.superhealth.engine.devices.garmin.hrm.GarminHrmProPlusCoordinator;
import app.superhealth.engine.devices.garmin.watches.cirqa.GarminCirqaCoordinator;
import app.superhealth.engine.devices.garmin.watches.d2.GarminD2AirCoordinator;
import app.superhealth.engine.devices.garmin.watches.d2.GarminD2AirX10Coordinator;
import app.superhealth.engine.devices.garmin.watches.d2.GarminD2AirX15Coordinator;
import app.superhealth.engine.devices.garmin.watches.d2.GarminD2Mach1Coordinator;
import app.superhealth.engine.devices.garmin.watches.d2.GarminD2Mach1ProCoordinator;
import app.superhealth.engine.devices.garmin.watches.d2.GarminD2Mach2Coordinator;
import app.superhealth.engine.devices.garmin.watches.d2.GarminD2Mach2ProCoordinator;
import app.superhealth.engine.devices.garmin.watches.descent.GarminDescentG1Coordinator;
import app.superhealth.engine.devices.garmin.watches.descent.GarminDescentG2Coordinator;
import app.superhealth.engine.devices.garmin.watches.descent.GarminDescentMk3Coordinator;
import app.superhealth.engine.devices.garmin.watches.descent.GarminDescentMk3iCoordinator;
import app.superhealth.engine.devices.garmin.watches.enduro.GarminEnduro2Coordinator;
import app.superhealth.engine.devices.garmin.watches.enduro.GarminEnduro3Coordinator;
import app.superhealth.engine.devices.garmin.watches.enduro.GarminEnduro4Coordinator;
import app.superhealth.engine.devices.garmin.watches.enduro.GarminEnduroCoordinator;
import app.superhealth.engine.devices.garmin.watches.epix.GarminEpixCoordinator;
import app.superhealth.engine.devices.garmin.watches.epix.GarminEpixGen2Coordinator;
import app.superhealth.engine.devices.garmin.watches.epix.GarminEpixProCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix2Coordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix3ChronosCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix3Coordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix3HrCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix5Coordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix5PlusCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix5SCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix5SPlusCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix5XCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix5XPlusCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix6Coordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix6ProCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix6SCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix6SProCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix6SSportCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix6SportCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix6SProSolarCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix6SSapphireCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix6SapphireCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix6XCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix6XProCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix6XProSolarCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix6XSapphireCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix7Coordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix7ProCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix7ProSolarCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix7SCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix7SProCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix7SProSolarCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix7XCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix7XProCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix7XProSolarCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix8Coordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix8ProCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix8SolarCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix9Coordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix9ProCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix9ProSolarCoordinator;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenixECoordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner165Coordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner165MusicCoordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner170Coordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner170MusicCoordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner220Coordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner225Coordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner230Coordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner235Coordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner245Coordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner245MusicCoordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner255Coordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner255MusicCoordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner255SCoordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner255SMusicCoordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner25Coordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner265Coordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner265SCoordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner30Coordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner35Coordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner45Coordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner45PlusCoordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner55Coordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner570Coordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner620Coordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner630Coordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner645Coordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner645MusicCoordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner70Coordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner735XTCoordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner745Coordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner920Coordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner920XTCoordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner935Coordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner945Coordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner945LTECoordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner955Coordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner965Coordinator;
import app.superhealth.engine.devices.garmin.watches.forerunner.GarminForerunner970Coordinator;
import app.superhealth.engine.devices.garmin.watches.instinct.GarminInstinct2Coordinator;
import app.superhealth.engine.devices.garmin.watches.instinct.GarminInstinct2SCoordinator;
import app.superhealth.engine.devices.garmin.watches.instinct.GarminInstinct2SSolarCoordinator;
import app.superhealth.engine.devices.garmin.watches.instinct.GarminInstinct2SSurfCoordinator;
import app.superhealth.engine.devices.garmin.watches.instinct.GarminInstinct2SolTacCoordinator;
import app.superhealth.engine.devices.garmin.watches.instinct.GarminInstinct2SolarCoordinator;
import app.superhealth.engine.devices.garmin.watches.instinct.GarminInstinct2XCoordinator;
import app.superhealth.engine.devices.garmin.watches.instinct.GarminInstinct2XSolarCoordinator;
import app.superhealth.engine.devices.garmin.watches.instinct.GarminInstinct2XTacticalCoordinator;
import app.superhealth.engine.devices.garmin.watches.instinct.GarminInstinct3Coordinator;
import app.superhealth.engine.devices.garmin.watches.instinct.GarminInstinct3SolarCoordinator;
import app.superhealth.engine.devices.garmin.watches.instinct.GarminInstinct3SolarTacticalCoordinator;
import app.superhealth.engine.devices.garmin.watches.instinct.GarminInstinct3TacticalCoordinator;
import app.superhealth.engine.devices.garmin.watches.instinct.GarminInstinctCoordinator;
import app.superhealth.engine.devices.garmin.watches.instinct.GarminInstinctCrossoverCoordinator;
import app.superhealth.engine.devices.garmin.watches.instinct.GarminInstinctECoordinator;
import app.superhealth.engine.devices.garmin.watches.instinct.GarminInstinctSolarCoordinator;
import app.superhealth.engine.devices.garmin.watches.instinct.GarminInstinctSolarTacticalCoordinator;
import app.superhealth.engine.devices.garmin.watches.instinct.GarminInstinctTacticalCoordinator;
import app.superhealth.engine.devices.garmin.watches.lily.GarminLily2ActiveCoordinator;
import app.superhealth.engine.devices.garmin.watches.lily.GarminLily2Coordinator;
import app.superhealth.engine.devices.garmin.watches.lily.GarminLilyCoordinator;
import app.superhealth.engine.devices.garmin.watches.quatix.GarminQuatix6Coordinator;
import app.superhealth.engine.devices.garmin.watches.quatix.GarminQuatix6XCoordinator;
import app.superhealth.engine.devices.garmin.watches.quatix.GarminQuatix8Coordinator;
import app.superhealth.engine.devices.garmin.watches.swim.GarminSwim2Coordinator;
import app.superhealth.engine.devices.garmin.watches.tactix.GarminTactix7Coordinator;
import app.superhealth.engine.devices.garmin.watches.tactix.GarminTactix8Coordinator;
import app.superhealth.engine.devices.garmin.watches.tactix.GarminTactix8SolarCoordinator;
import app.superhealth.engine.devices.garmin.watches.venu.GarminVenu2Coordinator;
import app.superhealth.engine.devices.garmin.watches.venu.GarminVenu2PlusCoordinator;
import app.superhealth.engine.devices.garmin.watches.venu.GarminVenu2SCoordinator;
import app.superhealth.engine.devices.garmin.watches.venu.GarminVenu3Coordinator;
import app.superhealth.engine.devices.garmin.watches.venu.GarminVenu3SCoordinator;
import app.superhealth.engine.devices.garmin.watches.venu.GarminVenu4Coordinator;
import app.superhealth.engine.devices.garmin.watches.venu.GarminVenuCoordinator;
import app.superhealth.engine.devices.garmin.watches.venu.GarminVenuSq2Coordinator;
import app.superhealth.engine.devices.garmin.watches.venu.GarminVenuSq2MusicCoordinator;
import app.superhealth.engine.devices.garmin.watches.venu.GarminVenuSqCoordinator;
import app.superhealth.engine.devices.garmin.watches.venu.GarminVenuSqMusicCoordinator;
import app.superhealth.engine.devices.garmin.watches.venu.GarminVenuX1Coordinator;
import app.superhealth.engine.devices.garmin.watches.vivoactive.GarminVivoActive3Coordinator;
import app.superhealth.engine.devices.garmin.watches.vivoactive.GarminVivoActive3MusicCoordinator;
import app.superhealth.engine.devices.garmin.watches.vivoactive.GarminVivoActive3TCoordinator;
import app.superhealth.engine.devices.garmin.watches.vivoactive.GarminVivoActive4Coordinator;
import app.superhealth.engine.devices.garmin.watches.vivoactive.GarminVivoActive4SCoordinator;
import app.superhealth.engine.devices.garmin.watches.vivoactive.GarminVivoActive5Coordinator;
import app.superhealth.engine.devices.garmin.watches.vivoactive.GarminVivoActive6Coordinator;
import app.superhealth.engine.devices.garmin.watches.vivoactive.GarminVivoActiveCoordinator;
import app.superhealth.engine.devices.garmin.watches.vivoactive.GarminVivoActiveHrCoordinator;
import app.superhealth.engine.devices.garmin.watches.vivomove.GarminVivomoveCoordinator;
import app.superhealth.engine.devices.garmin.watches.vivomove.GarminVivomoveHrCoordinator;
import app.superhealth.engine.devices.garmin.watches.vivomove.GarminVivomoveSportCoordinator;
import app.superhealth.engine.devices.garmin.watches.vivomove.GarminVivomoveStyleCoordinator;
import app.superhealth.engine.devices.garmin.watches.vivomove.GarminVivomoveTrendCoordinator;
import app.superhealth.engine.devices.garmin.watches.vivosmart.GarminVivosmart3Coordinator;
import app.superhealth.engine.devices.garmin.watches.vivosmart.GarminVivosmart4Coordinator;
import app.superhealth.engine.devices.garmin.watches.vivosmart.GarminVivosmart5Coordinator;
import app.superhealth.engine.devices.garmin.watches.vivosmart.GarminVivosmartCoordinator;
import app.superhealth.engine.devices.garmin.watches.vivosmart.GarminVivosmartHrCoordinator;
import app.superhealth.engine.devices.garmin.watches.vivosmart.GarminVivosmartHrPlusCoordinator;
import app.superhealth.engine.devices.garmin.watches.vivosport.GarminVivosportCoordinator;
import app.superhealth.engine.devices.generic_headphones.GenericHeadphonesCoordinator;
import app.superhealth.engine.devices.generic_hr.GenericHeartRateCoordinator;
import app.superhealth.engine.devices.generic_scale.GenericWeightScaleCoordinator;
import app.superhealth.engine.devices.gloryfit.watches.BlackviewR3MaxCoordinator;
import app.superhealth.engine.devices.gloryfit.watches.ColmiP10Coordinator;
import app.superhealth.engine.devices.gloryfit.watches.D3Coordinator;
import app.superhealth.engine.devices.gloryfit.watches.DotnP66DCoordinator;
import app.superhealth.engine.devices.gloryfit.watches.GrvFc1Coordinator;
import app.superhealth.engine.devices.gloryfit.watches.HaylouWatch2ProCoordinator;
import app.superhealth.engine.devices.gloryfit.watches.HiFutureZone2Coordinator;
import app.superhealth.engine.devices.gloryfit.watches.ImikiFrame2Coordinator;
import app.superhealth.engine.devices.gloryfit.watches.KieslectCallingSmartwatchKrProCoordinator;
import app.superhealth.engine.devices.gloryfit.watches.OukitelBT103Coordinator;
import app.superhealth.engine.devices.gloryfit.watches.QecnatoS10Coordinator;
import app.superhealth.engine.devices.gloryfit.watches.R1Coordinator;
import app.superhealth.engine.devices.gloryfit.watches.S52Coordinator;
import app.superhealth.engine.devices.gloryfit.watches.SwissPeakCoordinator;
import app.superhealth.engine.devices.gloryfit.watches.T70Coordinator;
import app.superhealth.engine.devices.gloryfit.watches.Y66Coordinator;
import app.superhealth.engine.devices.gloryfit.watches.Y6Coordinator;
import app.superhealth.engine.devices.gloryfitpro.watches.DM58Coordinator;
import app.superhealth.engine.devices.gree.GreeAcCoordinator;
import app.superhealth.engine.devices.hama.fit6900.HamaFit6900DeviceCoordinator;
import app.superhealth.engine.devices.huawei.huaweiwatch6.HuaweiWatch6Coordinator;
import app.superhealth.engine.devices.huawei.huaweiwatch6.HuaweiWatch6ProCoordinator;
import app.superhealth.engine.devices.huawei.huaweiwatchgt7.HuaweiWatchGT7Coordinator;
import app.superhealth.engine.devices.huawei.huaweiwatchgt7.HuaweiWatchGT7ProCoordinator;
import app.superhealth.engine.devices.moyoung.C20ProCoordinator;
import app.superhealth.engine.devices.moyoung.ColmiP81Coordinator;
import app.superhealth.engine.devices.moyoung.FireBoltt069Coordinator;
import app.superhealth.engine.devices.moyoung.FireBolttNinjaPhantomCoordinator;
import app.superhealth.engine.devices.moyoung.GarettEvaCoordinator;
import app.superhealth.engine.devices.haylou.HaylouS35AncCoordinator;
import app.superhealth.engine.devices.hplus.EXRIZUK8Coordinator;
import app.superhealth.engine.devices.hplus.HPlusCoordinator;
import app.superhealth.engine.devices.hplus.MakibesF68Coordinator;
import app.superhealth.engine.devices.hplus.Q8Coordinator;
import app.superhealth.engine.devices.hplus.SG2Coordinator;
import app.superhealth.engine.devices.huami.amazfitband5.AmazfitBand5Coordinator;
import app.superhealth.engine.devices.huami.amazfitbip.AmazfitBipCoordinator;
import app.superhealth.engine.devices.huami.amazfitbip.AmazfitBipLiteCoordinator;
import app.superhealth.engine.devices.huami.amazfitbip3.AmazfitBip3Coordinator;
import app.superhealth.engine.devices.huami.amazfitbip3pro.AmazfitBip3ProCoordinator;
import app.superhealth.engine.devices.huami.amazfitbips.AmazfitBipSCoordinator;
import app.superhealth.engine.devices.huami.amazfitbips.AmazfitBipSLiteCoordinator;
import app.superhealth.engine.devices.huami.amazfitbipu.AmazfitBipUCoordinator;
import app.superhealth.engine.devices.huami.amazfitbipupro.AmazfitBipUProCoordinator;
import app.superhealth.engine.devices.huami.amazfitcor.AmazfitCorCoordinator;
import app.superhealth.engine.devices.huami.amazfitcor2.AmazfitCor2Coordinator;
import app.superhealth.engine.devices.huami.amazfitgtr.AmazfitGTRCoordinator;
import app.superhealth.engine.devices.huami.amazfitgtr.AmazfitGTRLiteCoordinator;
import app.superhealth.engine.devices.huami.amazfitgtr2.AmazfitGTR2Coordinator;
import app.superhealth.engine.devices.huami.amazfitgtr2.AmazfitGTR2eCoordinator;
import app.superhealth.engine.devices.huami.amazfitgts.AmazfitGTSCoordinator;
import app.superhealth.engine.devices.huami.amazfitgts2.AmazfitGTS2Coordinator;
import app.superhealth.engine.devices.huami.amazfitgts2.AmazfitGTS2MiniCoordinator;
import app.superhealth.engine.devices.huami.amazfitgts2.AmazfitGTS2eCoordinator;
import app.superhealth.engine.devices.huami.amazfitneo.AmazfitNeoCoordinator;
import app.superhealth.engine.devices.huami.amazfitpop.AmazfitPopCoordinator;
import app.superhealth.engine.devices.huami.amazfitpoppro.AmazfitPopProCoordinator;
import app.superhealth.engine.devices.huami.amazfittrex.AmazfitTRexCoordinator;
import app.superhealth.engine.devices.huami.amazfittrexpro.AmazfitTRexProCoordinator;
import app.superhealth.engine.devices.huami.amazfitvergel.AmazfitVergeLCoordinator;
import app.superhealth.engine.devices.huami.amazfitx.AmazfitXCoordinator;
import app.superhealth.engine.devices.huami.miband2.MiBand2Coordinator;
import app.superhealth.engine.devices.huami.miband2.MiBand2HRXCoordinator;
import app.superhealth.engine.devices.huami.miband3.MiBand3Coordinator;
import app.superhealth.engine.devices.huami.miband4.MiBand4Coordinator;
import app.superhealth.engine.devices.huami.miband5.MiBand5Coordinator;
import app.superhealth.engine.devices.huami.miband6.MiBand6Coordinator;
import app.superhealth.engine.devices.huami.zeppe.ZeppECoordinator;
import app.superhealth.engine.devices.huami.zeppos.rings.AmazfitHelioRingCoordinator;
import app.superhealth.engine.devices.huami.zeppos.straps.AmazfitHelioStrapCoordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitActive2NfcCoordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitActive2RoundCoordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitActive2SquareCoordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitActive3PremiumCoordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitActiveCoordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitActiveEdgeCoordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitActiveMaxCoordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitBalance2Coordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitBalance2XTCoordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitBalance3Coordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitBalanceCoordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitBalanceUltraCoordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitBand7Coordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitBip5Coordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitBip5UnityCoordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitBip6Coordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitBipMaxCoordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitCheetah2ProCoordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitCheetah2UltraCoordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitCheetahProCoordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitCheetahRoundCoordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitCheetahSquareCoordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitFalconCoordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitGTR3Coordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitGTR3ProCoordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitGTR4Coordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitGTRMiniCoordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitGTS3Coordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitGTS4Coordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitGTS4MiniCoordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitGTS4MiniNewCoordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitTRex2Coordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitTRex3Coordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitTRex3Pro44mmCoordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitTRex3Pro48mmCoordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitTRexUltra2Coordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.AmazfitTRexUltraCoordinator;
import app.superhealth.engine.devices.huami.zeppos.watches.MiBand7Coordinator;
import app.superhealth.engine.devices.huawei.freearc.HuaweiFreearcCoordinator;
import app.superhealth.engine.devices.huawei.freebuds5i.HuaweiFreebuds5iCoordinator;
import app.superhealth.engine.devices.huawei.freebuds6.HuaweiFreebuds6Coordinator;
import app.superhealth.engine.devices.huawei.freebudspro.HuaweiFreebudsProCoordinator;
import app.superhealth.engine.devices.huawei.freeclip.HuaweiFreeClip2Coordinator;
import app.superhealth.engine.devices.huawei.honorband10.HonorBand10Coordinator;
import app.superhealth.engine.devices.huawei.honorband3.HonorBand3Coordinator;
import app.superhealth.engine.devices.huawei.honorband4.HonorBand4Coordinator;
import app.superhealth.engine.devices.huawei.honorband5.HonorBand5Coordinator;
import app.superhealth.engine.devices.huawei.honorband6.HonorBand6Coordinator;
import app.superhealth.engine.devices.huawei.honorband7.HonorBand7Coordinator;
import app.superhealth.engine.devices.huawei.honorband9.HonorBand9Coordinator;
import app.superhealth.engine.devices.huawei.honormagicwatch.HonorMagicWatchCoordinator;
import app.superhealth.engine.devices.huawei.honormagicwatch2.HonorMagicWatch2Coordinator;
import app.superhealth.engine.devices.huawei.honorwatch4.HonorWatch4Coordinator;
import app.superhealth.engine.devices.huawei.honorwatch5.HonorWatch5Coordinator;
import app.superhealth.engine.devices.huawei.honorwatch5pro.HonorWatch5ProCoordinator;
import app.superhealth.engine.devices.huawei.honorwatch6.HonorWatch6Coordinator;
import app.superhealth.engine.devices.huawei.honorwatchgs3.HonorWatchGS3Coordinator;
import app.superhealth.engine.devices.huawei.honorwatchgspro.HonorWatchGSProCoordinator;
import app.superhealth.engine.devices.huawei.huaweiband10.HuaweiBand10Coordinator;
import app.superhealth.engine.devices.huawei.huaweiband11.HuaweiBand11Coordinator;
import app.superhealth.engine.devices.huawei.huaweiband11pro.HuaweiBand11ProCoordinator;
import app.superhealth.engine.devices.huawei.huaweiband2pro.HuaweiBand2ProCoordinator;
import app.superhealth.engine.devices.huawei.huaweiband3pro.HuaweiBand3ProCoordinator;
import app.superhealth.engine.devices.huawei.huaweiband4pro.HuaweiBand4ProCoordinator;
import app.superhealth.engine.devices.huawei.huaweiband6.HuaweiBand6Coordinator;
import app.superhealth.engine.devices.huawei.huaweiband7.HuaweiBand7Coordinator;
import app.superhealth.engine.devices.huawei.huaweiband8.HuaweiBand8Coordinator;
import app.superhealth.engine.devices.huawei.huaweiband9.HuaweiBand9Coordinator;
import app.superhealth.engine.devices.huawei.huaweibandaw70.HuaweiBandAw70Coordinator;
import app.superhealth.engine.devices.huawei.huaweitalkbandb6.HuaweiTalkBandB6Coordinator;
import app.superhealth.engine.devices.huawei.huaweiwatch3.HuaweiWatch3Coordinator;
import app.superhealth.engine.devices.huawei.huaweiwatch4pro.HuaweiWatch4ProCoordinator;
import app.superhealth.engine.devices.huawei.huaweiwatch5.HuaweiWatch5Coordinator;
import app.superhealth.engine.devices.huawei.huaweiwatchd2.HuaweiWatchD2Coordinator;
import app.superhealth.engine.devices.huawei.huaweiwatchd3.HuaweiWatchD3Coordinator;
import app.superhealth.engine.devices.huawei.huaweiwatchfit.HuaweiWatchFitCoordinator;
import app.superhealth.engine.devices.huawei.huaweiwatchfit2.HuaweiWatchFit2Coordinator;
import app.superhealth.engine.devices.huawei.huaweiwatchfit3.HuaweiWatchFit3Coordinator;
import app.superhealth.engine.devices.huawei.huaweiwatchfit4pro.HuaweiWatchFit4ProCoordinator;
import app.superhealth.engine.devices.huawei.huaweiwatchfit5.HuaweiWatchFit5Coordinator;
import app.superhealth.engine.devices.huawei.huaweiwatchfit5pro.HuaweiWatchFit5ProCoordinator;
import app.superhealth.engine.devices.huawei.huaweiwatchgt.HuaweiWatchGTCoordinator;
import app.superhealth.engine.devices.huawei.huaweiwatchgt2.HuaweiWatchGT2Coordinator;
import app.superhealth.engine.devices.huawei.huaweiwatchgt2e.HuaweiWatchGT2eCoordinator;
import app.superhealth.engine.devices.huawei.huaweiwatchgt3.HuaweiWatchGT3Coordinator;
import app.superhealth.engine.devices.huawei.huaweiwatchgt4.HuaweiWatchGT4Coordinator;
import app.superhealth.engine.devices.huawei.huaweiwatchgt5.HuaweiWatchGT5Coordinator;
import app.superhealth.engine.devices.huawei.huaweiwatchgt6.HuaweiWatchGT6Coordinator;
import app.superhealth.engine.devices.huawei.huaweiwatchgtcyber.HuaweiWatchGTCyberCoordinator;
import app.superhealth.engine.devices.huawei.huaweiwatchgtrunner.HuaweiWatchGTRunnerCoordinator;
import app.superhealth.engine.devices.huawei.huaweiwatchgtrunner2.HuaweiWatchGTRunner2Coordinator;
import app.superhealth.engine.devices.huawei.huaweiwatchultimate.HuaweiWatchUltimateCoordinator;
import app.superhealth.engine.devices.huawei.huaweiwatchultimate2.HuaweiWatchUltimate2Coordinator;
import app.superhealth.engine.devices.id115.ID115Coordinator;
import app.superhealth.engine.devices.idasen.IdasenCoordinator;
import app.superhealth.engine.devices.igpsport.IGPSportBSC100MaxCoordinator;
import app.superhealth.engine.devices.igpsport.IGPSportBSC200Coordinator;
import app.superhealth.engine.devices.igpsport.IGPSportBSC200SCoordinator;
import app.superhealth.engine.devices.igpsport.IGPSportBSC300Coordinator;
import app.superhealth.engine.devices.igpsport.IGPSportBSC500Coordinator;
import app.superhealth.engine.devices.igpsport.IGPSportBiNaviAirCoordinator;
import app.superhealth.engine.devices.igpsport.IGPSportBiNaviCoordinator;
import app.superhealth.engine.devices.igpsport.IGPSportiGS630Coordinator;
import app.superhealth.engine.devices.igpsport.IGPSportiGS630SCoordinator;
import app.superhealth.engine.devices.igpsport.IGPSportiGS800Coordinator;
import app.superhealth.engine.devices.innovo.InnovoPulseOximeterCoordinator;
import app.superhealth.engine.devices.itag.ITagCoordinator;
import app.superhealth.engine.devices.jabra.JabraEvolve255Coordinator;
import app.superhealth.engine.devices.jabra.JabraEvolve265Coordinator;
import app.superhealth.engine.devices.jabra.JabraEvolve65Coordinator;
import app.superhealth.engine.devices.jyou.BFH16DeviceCoordinator;
import app.superhealth.engine.devices.jyou.TeclastH30.TeclastH30Coordinator;
import app.superhealth.engine.devices.jyou.y5.Y5Coordinator;
import app.superhealth.engine.devices.keephealth.C60DeviceCoordinator;
import app.superhealth.engine.devices.laxasfit.LaxasFitCoordinator;
import app.superhealth.engine.devices.lefun.BohemicSmartBraceletDeviceCoordinator;
import app.superhealth.engine.devices.lefun.LefunDeviceCoordinator;
import app.superhealth.engine.devices.lefun.VivitarHrBpMonitorActivityTrackerCoordinator;
import app.superhealth.engine.devices.lenovo.watch9.Watch9DeviceCoordinator;
import app.superhealth.engine.devices.lenovo.watchxplus.WatchXPlusDeviceCoordinator;
import app.superhealth.engine.devices.liveview.LiveviewCoordinator;
import app.superhealth.engine.devices.makibeshr3.MakibesHR3Coordinator;
import app.superhealth.engine.devices.marstek.MarstekB2500DeviceCoordinator;
import app.superhealth.engine.devices.miband.MiBandCoordinator;
import app.superhealth.engine.devices.mijia_lywsd.MijiaLywsd02Coordinator;
import app.superhealth.engine.devices.mijia_lywsd.MijiaLywsd03Coordinator;
import app.superhealth.engine.devices.mijia_lywsd.MijiaMhoC303Coordinator;
import app.superhealth.engine.devices.mijia_lywsd.MijiaXmwsdj04Coordinator;
import app.superhealth.engine.devices.miscale.MiCompositionScaleCoordinator;
import app.superhealth.engine.devices.miscale.MiSmartScaleCoordinator;
import app.superhealth.engine.devices.ninebot.NinebotScooterCoordinator;
import app.superhealth.engine.devices.moyoung.KW19BCoordinator;
import app.superhealth.engine.devices.onebyone.OneByoneScaleCoordinator;
import app.superhealth.engine.devices.moondrop.MoondropSpaceTravel2Coordinator;
import app.superhealth.engine.devices.moondrop.MoondropSpaceTravel2UltraCoordinator;
import app.superhealth.engine.devices.moondrop.MoondropSpaceTravelCoordinator;
import app.superhealth.engine.devices.moyoung.AdvanWatchSE1AICoordinator;
import app.superhealth.engine.devices.moyoung.AltVibeCoordinator;
import app.superhealth.engine.devices.moyoung.BettdowFB051Coordinator;
import app.superhealth.engine.devices.moyoung.BlackviewR60Coordinator;
import app.superhealth.engine.devices.moyoung.C20Coordinator;
import app.superhealth.engine.devices.moyoung.ColmiI28UltraCoordinator;
import app.superhealth.engine.devices.moyoung.ColmiP76Coordinator;
import app.superhealth.engine.devices.moyoung.ColmiP80Coordinator;
import app.superhealth.engine.devices.moyoung.ColmiV72Coordinator;
import app.superhealth.engine.devices.moyoung.ColmiV73Coordinator;
import app.superhealth.engine.devices.moyoung.ColmiV76Coordinator;
import app.superhealth.engine.devices.moyoung.ColmiV89Coordinator;
import app.superhealth.engine.devices.moyoung.FireBolttTalkCoordinator;
import app.superhealth.engine.devices.moyoung.HC96Coordinator;
import app.superhealth.engine.devices.moyoung.ITechActive3Coordinator;
import app.superhealth.engine.devices.moyoung.KT80Coordinator;
import app.superhealth.engine.devices.moyoung.KsixVentureCoordinator;
import app.superhealth.engine.devices.moyoung.L70Coordinator;
import app.superhealth.engine.devices.moyoung.MT55Coordinator;
import app.superhealth.engine.devices.moyoung.MisirunC17Coordinator;
import app.superhealth.engine.devices.moyoung.P22B1Coordinator;
import app.superhealth.engine.devices.moyoung.R50ProCoordinator;
import app.superhealth.engine.devices.moyoung.RainbuvvyT97Coordinator;
import app.superhealth.engine.devices.moyoung.ViranC29Coordinator;
import app.superhealth.engine.devices.moyoung.ZL02DCoordinator;
import app.superhealth.engine.devices.no1f1.No1F1Coordinator;
import app.superhealth.engine.devices.nothing.CmfBuds2Coordinator;
import app.superhealth.engine.devices.nothing.CmfBuds2PlusCoordinator;
import app.superhealth.engine.devices.nothing.CmfBuds2aCoordinator;
import app.superhealth.engine.devices.nothing.CmfBudsPro2Coordinator;
import app.superhealth.engine.devices.nothing.Ear1Coordinator;
import app.superhealth.engine.devices.nothing.Ear2Coordinator;
import app.superhealth.engine.devices.nothing.EarACoordinator;
import app.superhealth.engine.devices.nothing.EarStickCoordinator;
import app.superhealth.engine.devices.nut.NutCoordinator;
import app.superhealth.engine.devices.nxwear.NxWearB8Coordinator;
import app.superhealth.engine.devices.ollee.OlleeDeviceCoordinator;
import app.superhealth.engine.devices.onemoresonoflow.OneMoreSonoFlowCoordinator;
import app.superhealth.engine.devices.onemoresonoflow.OneMoreSonoFlowSECoordinator;
import app.superhealth.engine.devices.onetouch.OneTouchCoordinator;
import app.superhealth.engine.devices.oppo.OppoEncoAir2Coordinator;
import app.superhealth.engine.devices.oppo.OppoEncoAirCoordinator;
import app.superhealth.engine.devices.oppo.OppoEncoBuds2Coordinator;
import app.superhealth.engine.devices.overmax.OVTouch26Coordinator;
import app.superhealth.engine.devices.pebble.PebbleCoordinator;
import app.superhealth.engine.devices.pinetime.PineTimeJFCoordinator;
import app.superhealth.engine.devices.pixel.PixelBudsACoordinator;
import app.superhealth.engine.devices.polar.PolarH10DeviceCoordinator;
import app.superhealth.engine.devices.polar.PolarH9DeviceCoordinator;
import app.superhealth.engine.devices.bose.NC700Coordinator;
import app.superhealth.engine.devices.bose.QC35Coordinator;
import app.superhealth.engine.devices.qhybrid.QHybridCoordinator;
import app.superhealth.engine.devices.qnscale.QnScaleCoordinator;
import app.superhealth.engine.devices.realme.RealmeBudsAir5ProCoordinator;
import app.superhealth.engine.devices.realme.RealmeBudsAir6ProCoordinator;
import app.superhealth.engine.devices.realme.RealmeBudsT100Coordinator;
import app.superhealth.engine.devices.realme.RealmeBudsT110Coordinator;
import app.superhealth.engine.devices.realme.RealmeBudsT200Coordinator;
import app.superhealth.engine.devices.realme.RealmeBudsT300Coordinator;
import app.superhealth.engine.devices.redmibuds.RedmiBuds3ProCoordinator;
import app.superhealth.engine.devices.redmibuds.RedmiBuds4ActiveCoordinator;
import app.superhealth.engine.devices.redmibuds.RedmiBuds5ProCoordinator;
import app.superhealth.engine.devices.redmibuds.RedmiBuds6ActiveCoordinator;
import app.superhealth.engine.devices.redmibuds.RedmiBuds6Coordinator;
import app.superhealth.engine.devices.redmibuds.RedmiBuds6LiteCoordinator;
import app.superhealth.engine.devices.redmibuds.RedmiBuds6ProCoordinator;
import app.superhealth.engine.devices.redmibuds.RedmiBuds8ActiveCoordinator;
import app.superhealth.engine.devices.roidmi.Roidmi1Coordinator;
import app.superhealth.engine.devices.roidmi.Roidmi3Coordinator;
import app.superhealth.engine.devices.roidmi.RoidmiF8Coordinator;
import app.superhealth.engine.devices.sbm_67.SanitasSBM67Coordinator;
import app.superhealth.engine.devices.sbm_67.SilverCrestSBM67Coordinator;
import app.superhealth.engine.devices.scannable.ScannableDeviceCoordinator;
import app.superhealth.engine.devices.sennheiser.SennheiserMomentumInEarWirelessCoordinator;
import app.superhealth.engine.devices.shokz.ShokzOpenRunPro2Coordinator;
import app.superhealth.engine.devices.shokz.ShokzOpenSwimProCoordinator;
import app.superhealth.engine.devices.sinilink.SinilinkCoordinator;
import app.superhealth.engine.devices.smaq2oss.SMAQ2OSSCoordinator;
import app.superhealth.engine.devices.soflow.SoFlowCoordinator;
import app.superhealth.engine.devices.sony.headphones.coordinators.SonyLinkBudsCoordinator;
import app.superhealth.engine.devices.sony.headphones.coordinators.SonyLinkBudsSCoordinator;
import app.superhealth.engine.devices.sony.headphones.coordinators.SonyWF1000XM3Coordinator;
import app.superhealth.engine.devices.sony.headphones.coordinators.SonyWF1000XM4Coordinator;
import app.superhealth.engine.devices.sony.headphones.coordinators.SonyWF1000XM5Coordinator;
import app.superhealth.engine.devices.sony.headphones.coordinators.SonyWFC500Coordinator;
import app.superhealth.engine.devices.sony.headphones.coordinators.SonyWFC510Coordinator;
import app.superhealth.engine.devices.sony.headphones.coordinators.SonyWFC700NCoordinator;
import app.superhealth.engine.devices.sony.headphones.coordinators.SonyWFC710NCoordinator;
import app.superhealth.engine.devices.sony.headphones.coordinators.SonyWFSP800NCoordinator;
import app.superhealth.engine.devices.sony.headphones.coordinators.SonyWH1000XM2Coordinator;
import app.superhealth.engine.devices.sony.headphones.coordinators.SonyWH1000XM3Coordinator;
import app.superhealth.engine.devices.sony.headphones.coordinators.SonyWH1000XM4Coordinator;
import app.superhealth.engine.devices.sony.headphones.coordinators.SonyWH1000XM5Coordinator;
import app.superhealth.engine.devices.sony.headphones.coordinators.SonyWH1000XM6Coordinator;
import app.superhealth.engine.devices.sony.headphones.coordinators.SonyWHCH720NCoordinator;
import app.superhealth.engine.devices.sony.headphones.coordinators.SonyWHULT900NCoordinator;
import app.superhealth.engine.devices.sony.headphones.coordinators.SonyWIC100Coordinator;
import app.superhealth.engine.devices.sony.headphones.coordinators.SonyWISP600NCoordinator;
import app.superhealth.engine.devices.sony.reonpocket.SonyReonPocketProCoordinator;
import app.superhealth.engine.devices.sony.wena3.SonyWena3Coordinator;
import app.superhealth.engine.devices.sonyswr12.SonySWR12DeviceCoordinator;
import app.superhealth.engine.devices.soundbrenner.SoundbrennerCoordinator;
import app.superhealth.engine.devices.soundcore.SoundcoreP42iCoordinator;
import app.superhealth.engine.devices.soundcore.SoundcoreP41iCoordinator;
import app.superhealth.engine.devices.soundcore.SoundcoreP40iCoordinator;
import app.superhealth.engine.devices.soundcore.SoundcoreP31iCoordinator;
import app.superhealth.engine.devices.soundcore.SoundcoreP3Coordinator;
import app.superhealth.engine.devices.soundcore.SoundcoreA1Coordinator;
import app.superhealth.engine.devices.soundcore.SoundcoreP2Coordinator;
import app.superhealth.engine.devices.soundcore.SoundcoreP2iCoordinator;
import app.superhealth.engine.devices.soundcore.SoundcoreP2MiniCoordinator;
import app.superhealth.engine.devices.soundcore.SoundcoreA2NCCoordinator;
import app.superhealth.engine.devices.soundcore.SoundcoreA3iCoordinator;
import app.superhealth.engine.devices.soundcore.SoundcoreP3iCoordinator;
import app.superhealth.engine.devices.soundcore.SoundcoreK20iCoordinator;
import app.superhealth.engine.devices.soundcore.SoundcoreP30iCoordinator;
import app.superhealth.engine.devices.soundcore.SoundcoreP20iCoordinator;
import app.superhealth.engine.devices.soundcore.SoundcoreA30iCoordinator;
import app.superhealth.engine.devices.soundcore.SoundcoreAeroFit2Coordinator;
import app.superhealth.engine.devices.soundcore.SoundcoreLiberty3ProCoordinator;
import app.superhealth.engine.devices.soundcore.SoundcoreLiberty4NCCoordinator;
import app.superhealth.engine.devices.soundcore.SoundcoreLifeTuneCoordinator;
import app.superhealth.engine.devices.soundcore.SoundcoreLifeTuneXRCoordinator;
import app.superhealth.engine.devices.soundcore.SoundcoreMotion300Coordinator;
import app.superhealth.engine.devices.soundcore.SoundcoreMotionPlusCoordinator;
import app.superhealth.engine.devices.soundcore.SoundcoreQ30Coordinator;
import app.superhealth.engine.devices.soundcore.SoundcoreSpaceOneCoordinator;
import app.superhealth.engine.devices.soundcore.SoundcoreSportX20Coordinator;
import app.superhealth.engine.devices.soundcore.SoundcoreV20iCoordinator;
import app.superhealth.engine.devices.supercars.SuperCarsCoordinator;
import app.superhealth.engine.devices.test.TestDeviceCoordinator;
import app.superhealth.engine.devices.teufel.TeufelMyndCoordinator;
import app.superhealth.engine.devices.thermalprinter.ThermalPrinterCoordinator;
import app.superhealth.engine.devices.tlw64.TLW64Coordinator;
import app.superhealth.engine.devices.ultrahuman.UltrahumanDeviceCoordinator;
import app.superhealth.engine.devices.ugreen.hitunemax5c.UgreenHituneMax5cCoordinator;
import app.superhealth.engine.devices.um25.Coordinator.UM25Coordinator;
import app.superhealth.engine.devices.una.UnaDeviceCoordinator;
import app.superhealth.engine.devices.vesc.VescCoordinator;
import app.superhealth.engine.devices.viatom.F8ScaleCoordinator;
import app.superhealth.engine.devices.vibratissimo.VibratissimoCoordinator;
import app.superhealth.engine.devices.victron.VictronSmartShuntCoordinator;
import app.superhealth.engine.devices.waspos.WaspOSCoordinator;
import app.superhealth.engine.devices.wearfit.WearFitCoordinator;
import app.superhealth.engine.devices.withingssteelhr.WithingsSteelHRDeviceCoordinator;
import app.superhealth.engine.devices.xiaomi.watches.MiBand10Coordinator;
import app.superhealth.engine.devices.xiaomi.watches.MiBand10ProCoordinator;
import app.superhealth.engine.devices.xiaomi.watches.MiBand11Coordinator;
import app.superhealth.engine.devices.xiaomi.watches.MiBand4CCoordinator;
import app.superhealth.engine.devices.xiaomi.watches.MiBand7ProCoordinator;
import app.superhealth.engine.devices.xiaomi.watches.MiBand8ActiveCoordinator;
import app.superhealth.engine.devices.xiaomi.watches.MiBand8Coordinator;
import app.superhealth.engine.devices.xiaomi.watches.MiBand8ProCoordinator;
import app.superhealth.engine.devices.xiaomi.watches.MiBand9ActiveCoordinator;
import app.superhealth.engine.devices.xiaomi.watches.MiBand9Coordinator;
import app.superhealth.engine.devices.xiaomi.watches.MiBand9ProCoordinator;
import app.superhealth.engine.devices.xiaomi.watches.MiWatchColorSportCoordinator;
import app.superhealth.engine.devices.xiaomi.watches.MiWatchLiteCoordinator;
import app.superhealth.engine.devices.xiaomi.watches.RedmiSmartBand2Coordinator;
import app.superhealth.engine.devices.xiaomi.watches.RedmiSmartBand3Coordinator;
import app.superhealth.engine.devices.xiaomi.watches.RedmiSmartBandProCoordinator;
import app.superhealth.engine.devices.xiaomi.watches.RedmiWatch2Coordinator;
import app.superhealth.engine.devices.xiaomi.watches.RedmiWatch2LiteCoordinator;
import app.superhealth.engine.devices.xiaomi.watches.RedmiWatch3ActiveCoordinator;
import app.superhealth.engine.devices.xiaomi.watches.RedmiWatch3Coordinator;
import app.superhealth.engine.devices.xiaomi.watches.RedmiWatch4Coordinator;
import app.superhealth.engine.devices.xiaomi.watches.RedmiWatch5ActiveCoordinator;
import app.superhealth.engine.devices.xiaomi.watches.RedmiWatch5Coordinator;
import app.superhealth.engine.devices.xiaomi.watches.RedmiWatch5LiteCoordinator;
import app.superhealth.engine.devices.xiaomi.watches.RedmiWatch6Coordinator;
import app.superhealth.engine.devices.xiaomi.watches.RedmiWatchMoveCoordinator;
import app.superhealth.engine.devices.xiaomi.watches.XiaomiWatch5Coordinator;
import app.superhealth.engine.devices.xiaomi.watches.XiaomiWatchS1ActiveCoordinator;
import app.superhealth.engine.devices.xiaomi.watches.XiaomiWatchS1Coordinator;
import app.superhealth.engine.devices.xiaomi.watches.XiaomiWatchS1ProCoordinator;
import app.superhealth.engine.devices.xiaomi.watches.XiaomiWatchS3Coordinator;
import app.superhealth.engine.devices.xiaomi.watches.XiaomiWatchS4Coordinator;
import app.superhealth.engine.devices.xiaomi.watches.XiaomiWatchS5Coordinator;
import app.superhealth.engine.devices.xiaomi_hipee.XiaomiHipeeP1Coordinator;
import app.superhealth.engine.devices.xiaomi_scooters.XiaomiScooter5MaxCoordinator;
import app.superhealth.engine.devices.zeblaze.Beyond3ProCoordinator;
import app.superhealth.engine.devices.zeblaze.stratos4pro.Stratos4ProCoordinator;
import app.superhealth.engine.devices.xplora.XploraXmoveDeviceCoordinator;
import app.superhealth.engine.devices.xwatch.XWatchCoordinator;
import app.superhealth.engine.devices.yawell.ring.ColmiR02Coordinator;
import app.superhealth.engine.devices.yawell.ring.ColmiR03Coordinator;
import app.superhealth.engine.devices.yawell.ring.ColmiR06Coordinator;
import app.superhealth.engine.devices.yawell.ring.ColmiR07Coordinator;
import app.superhealth.engine.devices.yawell.ring.ColmiR09Coordinator;
import app.superhealth.engine.devices.yawell.ring.ColmiR10Coordinator;
import app.superhealth.engine.devices.yawell.ring.ColmiR12Coordinator;
import app.superhealth.engine.devices.yawell.ring.H59Coordinator;
import app.superhealth.engine.devices.yawell.ring.YawellR05Coordinator;
import app.superhealth.engine.devices.yawell.ring.YawellR10Coordinator;
import app.superhealth.engine.devices.yawell.ring.YawellR11Coordinator;
import app.superhealth.engine.devices.yawell.ring.YawellY27Coordinator;
import app.superhealth.engine.devices.zendure.SolarFlowDeviceCoordinator;
import app.superhealth.engine.devices.zetime.ZeTimeCoordinator;
import app.superhealth.engine.service.devices.gatt_client.BleGattClientCoordinator;

/**
 * For every supported device, a device type constant must exist.
 * <p>
 * Note: they name of the enum is stored in the DB, so it is fixed forever,
 * and may not be changed.
 * <p>
 * Migration note: As of ticket #3347,
 * the numeric device id is not used anymore. If your database has development devices that still used
 * the numeric ID, you need to update assets/migrations/devicetype.json before installing SuperHealth
 * after rebasing, in order for your device to be migrated correctly. If you failed to do this and the
 * device is now not being displayed, please update the file and uncomment the call to migrateDeviceTypes
 * in GBApplication.
 */
public enum DeviceType {
    UNKNOWN(UnknownDeviceCoordinator.class),
    PEBBLE(PebbleCoordinator.class),
    MIBAND(MiBandCoordinator.class),
    MIBAND2(MiBand2Coordinator.class),
    MIBAND2_HRX(MiBand2HRXCoordinator.class),
    AMAZFITBIP(AmazfitBipCoordinator.class),
    AMAZFITCOR(AmazfitCorCoordinator.class),
    MIBAND3(MiBand3Coordinator.class),
    AMAZFITCOR2(AmazfitCor2Coordinator.class),
    MIBAND4(MiBand4Coordinator.class),
    MIBAND4C(MiBand4CCoordinator.class),
    AMAZFITBIP_LITE(AmazfitBipLiteCoordinator.class),
    AMAZFITGTR(AmazfitGTRCoordinator.class),
    AMAZFITGTS(AmazfitGTSCoordinator.class),
    AMAZFITBIPS(AmazfitBipSCoordinator.class),
    AMAZFITGTR_LITE(AmazfitGTRLiteCoordinator.class),
    AMAZFITTREX(AmazfitTRexCoordinator.class),
    MIBAND5(MiBand5Coordinator.class),
    AMAZFITBAND5(AmazfitBand5Coordinator.class),
    AMAZFITBIPS_LITE(AmazfitBipSLiteCoordinator.class),
    AMAZFITGTR2(AmazfitGTR2Coordinator.class),
    AMAZFITGTS2(AmazfitGTS2Coordinator.class),
    AMAZFITBIPU(AmazfitBipUCoordinator.class),
    AMAZFITVERGEL(AmazfitVergeLCoordinator.class),
    AMAZFITBIPUPRO(AmazfitBipUProCoordinator.class),
    AMAZFITNEO(AmazfitNeoCoordinator.class),
    AMAZFITGTS2_MINI(AmazfitGTS2MiniCoordinator.class),
    ZEPP_E(ZeppECoordinator.class),
    AMAZFITGTR2E(AmazfitGTR2eCoordinator.class),
    AMAZFITGTS2E(AmazfitGTS2eCoordinator.class),
    AMAZFITX(AmazfitXCoordinator.class),
    MIBAND6(MiBand6Coordinator.class),
    AMAZFITTREXPRO(AmazfitTRexProCoordinator.class),
    AMAZFITPOP(AmazfitPopCoordinator.class),
    AMAZFITPOPPRO(AmazfitPopProCoordinator.class),
    MIBAND7(MiBand7Coordinator.class),
    MIBAND7PRO(MiBand7ProCoordinator.class),
    MIBAND8(MiBand8Coordinator.class),
    MIBAND8ACTIVE(MiBand8ActiveCoordinator.class),
    MIBAND8PRO(MiBand8ProCoordinator.class),
    MIBAND9(MiBand9Coordinator.class),
    MIBAND9ACTIVE(MiBand9ActiveCoordinator.class),
    MIBAND9PRO(MiBand9ProCoordinator.class),
    MIBAND10(MiBand10Coordinator.class),
    MIBAND10PRO(MiBand10ProCoordinator.class),
    MIBAND11(MiBand11Coordinator.class),
    MIWATCHLITE(MiWatchLiteCoordinator.class),
    MIWATCHCOLORSPORT(MiWatchColorSportCoordinator.class),
    REDMIBUDS3PRO(RedmiBuds3ProCoordinator.class),
    REDMIBUDS4ACTIVE(RedmiBuds4ActiveCoordinator.class),
    REDMIBUDS5PRO(RedmiBuds5ProCoordinator.class),
    REDMIBUDS6(RedmiBuds6Coordinator.class),
    REDMIBUDS6ACTIVE(RedmiBuds6ActiveCoordinator.class),
    REDMIBUDS6LITE(RedmiBuds6LiteCoordinator.class),
    REDMIBUDS6PRO(RedmiBuds6ProCoordinator.class),
    REDMIBUDS8ACTIVE(RedmiBuds8ActiveCoordinator.class),
    REDMIWATCH3ACTIVE(RedmiWatch3ActiveCoordinator.class),
    REDMIWATCH3(RedmiWatch3Coordinator.class),
    REDMISMARTBAND2(RedmiSmartBand2Coordinator.class),
    REDMISMARTBAND3(RedmiSmartBand3Coordinator.class),
    REDMIWATCH2(RedmiWatch2Coordinator.class),
    REDMIWATCH2LITE(RedmiWatch2LiteCoordinator.class),
    REDMISMARTBANDPRO(RedmiSmartBandProCoordinator.class),
    REDMIWATCH4(RedmiWatch4Coordinator.class),
    REDMIWATCH5(RedmiWatch5Coordinator.class),
    REDMIWATCH5ACTIVE(RedmiWatch5ActiveCoordinator.class),
    REDMIWATCH5LITE(RedmiWatch5LiteCoordinator.class),
    REDMIWATCH6(RedmiWatch6Coordinator.class),
    REDMIWATCHMOVE(RedmiWatchMoveCoordinator.class),
    XIAOMI_WATCH_S1_ACTIVE(XiaomiWatchS1ActiveCoordinator.class),
    XIAOMI_WATCH_S1_PRO(XiaomiWatchS1ProCoordinator.class),
    XIAOMI_WATCH_S1(XiaomiWatchS1Coordinator.class),
    XIAOMI_WATCH_S3(XiaomiWatchS3Coordinator.class),
    XIAOMI_WATCH_S4(XiaomiWatchS4Coordinator.class),
    XIAOMI_WATCH_S5(XiaomiWatchS5Coordinator.class),
    XIAOMI_WATCH_5(XiaomiWatch5Coordinator.class),
    AMAZFITGTS3(AmazfitGTS3Coordinator.class),
    AMAZFITGTR3(AmazfitGTR3Coordinator.class),
    AMAZFITGTR4(AmazfitGTR4Coordinator.class),
    AMAZFITBAND7(AmazfitBand7Coordinator.class),
    AMAZFITGTS4(AmazfitGTS4Coordinator.class),
    AMAZFITGTS4MINI(AmazfitGTS4MiniCoordinator.class),
    AMAZFITGTS4MININEW(AmazfitGTS4MiniNewCoordinator.class),
    AMAZFITTREX2(AmazfitTRex2Coordinator.class),
    AMAZFITTREX3(AmazfitTRex3Coordinator.class),
    AMAZFITTREX3PRO44MM(AmazfitTRex3Pro44mmCoordinator.class),
    AMAZFITTREX3PRO48MM(AmazfitTRex3Pro48mmCoordinator.class),
    AMAZFITGTR3PRO(AmazfitGTR3ProCoordinator.class),
    AMAZFITBIP3(AmazfitBip3Coordinator.class),
    AMAZFITBIP3PRO(AmazfitBip3ProCoordinator.class),
    AMAZFITCHEETAHPRO(AmazfitCheetahProCoordinator.class),
    AMAZFITCHEETAH2PRO(AmazfitCheetah2ProCoordinator.class),
    AMAZFITCHEETAH2ULTRA(AmazfitCheetah2UltraCoordinator.class),
    AMAZFITCHEETAHSQUARE(AmazfitCheetahSquareCoordinator.class),
    AMAZFITCHEETAHROUND(AmazfitCheetahRoundCoordinator.class),
    AMAZFITBIP5(AmazfitBip5Coordinator.class),
    AMAZFITBIP5UNITY(AmazfitBip5UnityCoordinator.class),
    AMAZFITBIP6(AmazfitBip6Coordinator.class),
    AMAZFITBIPMAX(AmazfitBipMaxCoordinator.class),
    AMAZFITTREXULTRA(AmazfitTRexUltraCoordinator.class),
    AMAZFITTREXULTRA2(AmazfitTRexUltra2Coordinator.class),
    AMAZFITGTRMINI(AmazfitGTRMiniCoordinator.class),
    AMAZFITFALCON(AmazfitFalconCoordinator.class),
    AMAZFITBALANCE(AmazfitBalanceCoordinator.class),
    AMAZFITBALANCE2(AmazfitBalance2Coordinator.class),
    AMAZFITBALANCE2XT(AmazfitBalance2XTCoordinator.class),
    AMAZFITBALANCE3(AmazfitBalance3Coordinator.class),
    AMAZFITBALANCEULTRA(AmazfitBalanceUltraCoordinator.class),
    AMAZFITACTIVE(AmazfitActiveCoordinator.class),
    AMAZFITACTIVE2(AmazfitActive2RoundCoordinator.class),
    AMAZFITACTIVE2NFC(AmazfitActive2NfcCoordinator.class),
    AMAZFITACTIVE2SQUARE(AmazfitActive2SquareCoordinator.class),
    AMAZFITACTIVE3PREMIUM(AmazfitActive3PremiumCoordinator.class),
    AMAZFITACTIVEEDGE(AmazfitActiveEdgeCoordinator.class),
    AMAZFITACTIVEMAX(AmazfitActiveMaxCoordinator.class),
    AMAZFITHELIORING(AmazfitHelioRingCoordinator.class),
    AMAZFITHELIOSTRAP(AmazfitHelioStrapCoordinator.class),
    HPLUS(HPlusCoordinator.class),
    MAKIBESF68(MakibesF68Coordinator.class),
    EXRIZUK8(EXRIZUK8Coordinator.class),
    Q8(Q8Coordinator.class),
    SG2(SG2Coordinator.class),
    NO1F1(No1F1Coordinator.class),
    P22B1(P22B1Coordinator.class),
    TECLASTH30(TeclastH30Coordinator.class),
    Y5(Y5Coordinator.class),
    XWATCH(XWatchCoordinator.class),
    ZETIME(ZeTimeCoordinator.class),
    ID115(ID115Coordinator.class),
    WATCH9(Watch9DeviceCoordinator.class),
    WATCHXPLUS(WatchXPlusDeviceCoordinator.class),
    ROIDMI(Roidmi1Coordinator.class),
    ROIDMI3(Roidmi3Coordinator.class),
    ROIDMI_F8(RoidmiF8Coordinator.class),
    CASIOECBS100(CasioECBS100DeviceCoordinator.class),

    CASIOGB6900(CasioGB6900DeviceCoordinator.class),
    CASIOGBX100(CasioGBX100DeviceCoordinator.class),
    CASIOGBD200(CasioGBD200DeviceCoordinator.class),
    CASIOGWB5600(CasioGWB5600DeviceCoordinator.class),
    CASIOGMWB5000(CasioGMWB5000DeviceCoordinator.class),
    CASIOGGB100(CasioGGB100DeviceCoordinator.class),
    MISMARTSCALE(MiSmartScaleCoordinator.class),
    MICOMPOSITIONSCALE(MiCompositionScaleCoordinator.class),
    ONEBYONE_SCALE(OneByoneScaleCoordinator.class),
    BFH16(BFH16DeviceCoordinator.class),
    MAKIBESHR3(MakibesHR3Coordinator.class),
    OVTOUCH26(OVTouch26Coordinator.class),
    BANGLEJS(BangleJSCoordinator.class),
    FOSSILQHYBRID(QHybridCoordinator.class),
    TLW64(TLW64Coordinator.class),
    PINETIME_JF(PineTimeJFCoordinator.class),
    POLARH9(PolarH9DeviceCoordinator.class),
    POLARH10(PolarH10DeviceCoordinator.class),
    MIJIA_LYWSD02(MijiaLywsd02Coordinator.class),
    MIJIA_LYWSD03(MijiaLywsd03Coordinator.class),
    MIJIA_XMWSDJ04(MijiaXmwsdj04Coordinator.class),
    MIJIA_MHO_C303(MijiaMhoC303Coordinator.class),
    LEFUN(LefunDeviceCoordinator.class),
    VIVITAR_HR_BP_MONITOR_ACTIVITY_TRACKER(VivitarHrBpMonitorActivityTrackerCoordinator.class),
    BOHEMIC_SMART_BRACELET(BohemicSmartBraceletDeviceCoordinator.class),
    SMAQ2OSS(SMAQ2OSSCoordinator.class),
    FITPRO(FitProDeviceCoordinator.class),
    COLACAO21(ColaCao21Coordinator.class),
    COLACAO23(ColaCao23Coordinator.class),
    AK102(Ak102Coordinator.class),
    AK75_PRO(Ak75ProCoordinator.class),
    AK86(Ak86Coordinator.class),
    LAXASFIT_Q11(LaxasFitCoordinator.class),
    ITAG(ITagCoordinator.class),
    IKEA_IDASEN(IdasenCoordinator.class),
    NUTMINI(NutCoordinator.class),
    NX_WEAR_B8(NxWearB8Coordinator.class),
    VIVOMOVE_HR(GarminVivomoveHrCoordinator.class),
    GARMIN_HRM_PRO_PLUS(GarminHrmProPlusCoordinator.class),
    GARMIN_HRM_200(GarminHrm200Coordinator.class),
    GARMIN_HRM_600(GarminHrm600Coordinator.class),
    GARMIN_EDGE_25(GarminEdge25Coordinator.class),
    GARMIN_EDGE_130(GarminEdge130Coordinator.class),
    GARMIN_EDGE_130_PLUS(GarminEdge130PlusCoordinator.class),
    GARMIN_EDGE_510(GarminEdge510Coordinator.class),
    GARMIN_EDGE_520(GarminEdge520Coordinator.class),
    GARMIN_EDGE_520_PLUS(GarminEdge520PlusCoordinator.class),
    GARMIN_EDGE_530(GarminEdge530Coordinator.class),
    GARMIN_EDGE_540(GarminEdge540Coordinator.class),
    GARMIN_EDGE_550(GarminEdge550Coordinator.class),
    GARMIN_EDGE_810(GarminEdge810Coordinator.class),
    GARMIN_EDGE_820(GarminEdge820Coordinator.class),
    GARMIN_EDGE_830(GarminEdge830Coordinator.class),
    GARMIN_EDGE_840(GarminEdge840Coordinator.class),
    GARMIN_EDGE_850(GarminEdge850Coordinator.class),
    GARMIN_EDGE_1000(GarminEdge1000Coordinator.class),
    GARMIN_EDGE_1030(GarminEdge1030Coordinator.class),
    GARMIN_EDGE_1030_PLUS(GarminEdge1030PlusCoordinator.class),
    GARMIN_EDGE_1040(GarminEdge1040Coordinator.class),
    GARMIN_EDGE_1050(GarminEdge1050Coordinator.class),
    GARMIN_EDGE_EXPLORE(GarminEdgeExploreCoordinator.class),
    GARMIN_EDGE_EXPLORE_2(GarminEdgeExplore2Coordinator.class),
    GARMIN_EDGE_EXPLORE_820(GarminEdgeExplore820Coordinator.class),
    GARMIN_EDGE_EXPLORE_1000(GarminEdgeExplore1000Coordinator.class),
    GARMIN_EDGE_MTB(GarminEdgeMtbCoordinator.class),
    GARMIN_GPSMAP_66I(GarminGpsmap66iCoordinator.class),
    GARMIN_GPSMAP_66S(GarminGpsmap66sCoordinator.class),
    GARMIN_GPSMAP_66SR(GarminGpsmap66srCoordinator.class),
    GARMIN_GPSMAP_H1(GarminGpsmapH1Coordinator.class),
    GARMIN_ETREX_SE(GarminETrexSeCoordinator.class),
    GARMIN_ENDURO(GarminEnduroCoordinator.class),
    GARMIN_ENDURO_2(GarminEnduro2Coordinator.class),
    GARMIN_ENDURO_3(GarminEnduro3Coordinator.class),
    GARMIN_ENDURO_4(GarminEnduro4Coordinator.class),
    GARMIN_EPIX(GarminEpixCoordinator.class),
    GARMIN_EPIX_GEN2(GarminEpixGen2Coordinator.class),
    GARMIN_EPIX_PRO(GarminEpixProCoordinator.class),
    GARMIN_DESCENT_MK3(GarminDescentMk3Coordinator.class),
    GARMIN_DESCENT_MK3I(GarminDescentMk3iCoordinator.class),
    GARMIN_DESCENT_G1(GarminDescentG1Coordinator.class),
    GARMIN_DESCENT_G2(GarminDescentG2Coordinator.class),
    GARMIN_FENIX_2(GarminFenix2Coordinator.class),
    GARMIN_FENIX_3(GarminFenix3Coordinator.class),
    GARMIN_FENIX_3_CHRONOS(GarminFenix3ChronosCoordinator.class),
    GARMIN_FENIX_3_HR(GarminFenix3HrCoordinator.class),
    GARMIN_FENIX_5(GarminFenix5Coordinator.class),
    GARMIN_FENIX_5S(GarminFenix5SCoordinator.class),
    GARMIN_FENIX_5S_PLUS(GarminFenix5SPlusCoordinator.class),
    GARMIN_FENIX_5_PLUS(GarminFenix5PlusCoordinator.class),
    GARMIN_FENIX_5X(GarminFenix5XCoordinator.class),
    GARMIN_FENIX_5X_PLUS(GarminFenix5XPlusCoordinator.class),
    GARMIN_FENIX_6(GarminFenix6Coordinator.class),
    GARMIN_FENIX_6_SPORT(GarminFenix6SportCoordinator.class),
    GARMIN_FENIX_6_PRO(GarminFenix6ProCoordinator.class),
    GARMIN_FENIX_6_SAPPHIRE(GarminFenix6SapphireCoordinator.class),
    GARMIN_FENIX_6S_PRO(GarminFenix6SProCoordinator.class),
    GARMIN_FENIX_6S(GarminFenix6SCoordinator.class),
    GARMIN_FENIX_6S_SPORT(GarminFenix6SSportCoordinator.class),
    GARMIN_FENIX_6S_PRO_SOLAR(GarminFenix6SProSolarCoordinator.class),
    GARMIN_FENIX_6S_SAPPHIRE(GarminFenix6SSapphireCoordinator.class),
    GARMIN_FENIX_6X(GarminFenix6XCoordinator.class),
    GARMIN_FENIX_6X_PRO(GarminFenix6XProCoordinator.class),
    GARMIN_FENIX_6X_PRO_SOLAR(GarminFenix6XProSolarCoordinator.class),
    GARMIN_FENIX_6X_SAPPHIRE(GarminFenix6XSapphireCoordinator.class),
    GARMIN_FENIX_7(GarminFenix7Coordinator.class),
    GARMIN_FENIX_7S(GarminFenix7SCoordinator.class),
    GARMIN_FENIX_7S_PRO(GarminFenix7SProCoordinator.class),
    GARMIN_FENIX_7S_PRO_SOLAR(GarminFenix7SProSolarCoordinator.class),
    GARMIN_FENIX_7X(GarminFenix7XCoordinator.class),
    GARMIN_FENIX_7_PRO(GarminFenix7ProCoordinator.class),
    GARMIN_FENIX_7_PRO_SOLAR(GarminFenix7ProSolarCoordinator.class),
    GARMIN_FENIX_7X_PRO(GarminFenix7XProCoordinator.class),
    GARMIN_FENIX_7X_PRO_SOLAR(GarminFenix7XProSolarCoordinator.class),
    GARMIN_FENIX_8(GarminFenix8Coordinator.class),
    GARMIN_FENIX_8_SOLAR(GarminFenix8SolarCoordinator.class),
    GARMIN_FENIX_8_PRO(GarminFenix8ProCoordinator.class),
    GARMIN_FENIX_9(GarminFenix9Coordinator.class),
    GARMIN_FENIX_9_PRO(GarminFenix9ProCoordinator.class),
    GARMIN_FENIX_9_PRO_SOLAR(GarminFenix9ProSolarCoordinator.class),
    GARMIN_FENIX_E(GarminFenixECoordinator.class),
    GARMIN_FORERUNNER_25(GarminForerunner25Coordinator.class),
    GARMIN_FORERUNNER_30(GarminForerunner30Coordinator.class),
    GARMIN_FORERUNNER_35(GarminForerunner35Coordinator.class),
    GARMIN_FORERUNNER_45(GarminForerunner45Coordinator.class),
    GARMIN_FORERUNNER_45_PLUS(GarminForerunner45PlusCoordinator.class),
    GARMIN_FORERUNNER_55(GarminForerunner55Coordinator.class),
    GARMIN_FORERUNNER_165(GarminForerunner165Coordinator.class),
    GARMIN_FORERUNNER_165_MUSIC(GarminForerunner165MusicCoordinator.class),
    GARMIN_FORERUNNER_170(GarminForerunner170Coordinator.class),
    GARMIN_FORERUNNER_170_MUSIC(GarminForerunner170MusicCoordinator.class),
    GARMIN_FORERUNNER_220(GarminForerunner220Coordinator.class),
    GARMIN_FORERUNNER_225(GarminForerunner225Coordinator.class),
    GARMIN_FORERUNNER_230(GarminForerunner230Coordinator.class),
    GARMIN_FORERUNNER_235(GarminForerunner235Coordinator.class),
    GARMIN_FORERUNNER_245(GarminForerunner245Coordinator.class),
    GARMIN_FORERUNNER_245_MUSIC(GarminForerunner245MusicCoordinator.class),
    GARMIN_FORERUNNER_255(GarminForerunner255Coordinator.class),
    GARMIN_FORERUNNER_255_MUSIC(GarminForerunner255MusicCoordinator.class),
    GARMIN_FORERUNNER_255S(GarminForerunner255SCoordinator.class),
    GARMIN_FORERUNNER_255S_MUSIC(GarminForerunner255SMusicCoordinator.class),
    GARMIN_FORERUNNER_265(GarminForerunner265Coordinator.class),
    GARMIN_FORERUNNER_265S(GarminForerunner265SCoordinator.class),
    GARMIN_FORERUNNER_570(GarminForerunner570Coordinator.class),
    GARMIN_FORERUNNER_620(GarminForerunner620Coordinator.class),
    GARMIN_FORERUNNER_630(GarminForerunner630Coordinator.class),
    GARMIN_FORERUNNER_645(GarminForerunner645Coordinator.class),
    GARMIN_FORERUNNER_645_MUSIC(GarminForerunner645MusicCoordinator.class),
    GARMIN_FORERUNNER_70(GarminForerunner70Coordinator.class),
    GARMIN_FORERUNNER_735XT(GarminForerunner735XTCoordinator.class),
    GARMIN_FORERUNNER_745(GarminForerunner745Coordinator.class),
    GARMIN_FORERUNNER_920(GarminForerunner920Coordinator.class),
    GARMIN_FORERUNNER_920XT(GarminForerunner920XTCoordinator.class),
    GARMIN_FORERUNNER_935(GarminForerunner935Coordinator.class),
    GARMIN_FORERUNNER_945(GarminForerunner945Coordinator.class),
    GARMIN_FORERUNNER_945_LTE(GarminForerunner945LTECoordinator.class),
    GARMIN_FORERUNNER_955(GarminForerunner955Coordinator.class),
    GARMIN_FORERUNNER_965(GarminForerunner965Coordinator.class),
    GARMIN_FORERUNNER_970(GarminForerunner970Coordinator.class),
    GARMIN_QUATIX_6(GarminQuatix6Coordinator.class),
    GARMIN_QUATIX_6X(GarminQuatix6XCoordinator.class),
    GARMIN_QUATIX_8(GarminQuatix8Coordinator.class),
    GARMIN_TACTIX_7(GarminTactix7Coordinator.class),
    GARMIN_TACTIX_8(GarminTactix8Coordinator.class),
    GARMIN_TACTIX_8_SOLAR(GarminTactix8SolarCoordinator.class),
    GARMIN_SWIM_2(GarminSwim2Coordinator.class),
    GARMIN_INREACH_MINI_2(GarminInReachMini2.class),
    GARMIN_INSTINCT(GarminInstinctCoordinator.class),
    GARMIN_INSTINCT_SOLAR(GarminInstinctSolarCoordinator.class),
    GARMIN_INSTINCT_TACTICAL(GarminInstinctTacticalCoordinator.class),
    GARMIN_INSTINCT_SOLAR_TACTICAL(GarminInstinctSolarTacticalCoordinator.class),
    GARMIN_INSTINCT_2(GarminInstinct2Coordinator.class),
    GARMIN_INSTINCT_2S(GarminInstinct2SCoordinator.class),
    GARMIN_INSTINCT_2S_SOLAR(GarminInstinct2SSolarCoordinator.class),
    GARMIN_INSTINCT_2S_SURF(GarminInstinct2SSurfCoordinator.class),
    GARMIN_INSTINCT_2X(GarminInstinct2XCoordinator.class),
    GARMIN_INSTINCT_2X_SOLAR(GarminInstinct2XSolarCoordinator.class),
    GARMIN_INSTINCT_2X_TACTICAL(GarminInstinct2XTacticalCoordinator.class),
    GARMIN_INSTINCT_2_SOLAR(GarminInstinct2SolarCoordinator.class),
    GARMIN_INSTINCT_2_SOLTAC(GarminInstinct2SolTacCoordinator.class),
    GARMIN_INSTINCT_CROSSOVER(GarminInstinctCrossoverCoordinator.class),
    GARMIN_INSTINCT_E(GarminInstinctECoordinator.class),
    GARMIN_INSTINCT_3(GarminInstinct3Coordinator.class),
    GARMIN_INSTINCT_3_SOLAR(GarminInstinct3SolarCoordinator.class),
    GARMIN_INSTINCT_3_SOLAR_TACTICAL(GarminInstinct3SolarTacticalCoordinator.class),
    GARMIN_INSTINCT_3_TACTICAL(GarminInstinct3TacticalCoordinator.class),
    GARMIN_LILY(GarminLilyCoordinator.class),
    GARMIN_LILY_2(GarminLily2Coordinator.class),
    GARMIN_LILY_2_ACTIVE(GarminLily2ActiveCoordinator.class),
    GARMIN_VIVOMOVE(GarminVivomoveCoordinator.class),
    GARMIN_VIVOMOVE_SPORT(GarminVivomoveSportCoordinator.class),
    GARMIN_VIVOMOVE_STYLE(GarminVivomoveStyleCoordinator.class),
    GARMIN_VIVOMOVE_TREND(GarminVivomoveTrendCoordinator.class),
    GARMIN_VENU(GarminVenuCoordinator.class),
    GARMIN_VENU_SQ(GarminVenuSqCoordinator.class),
    GARMIN_VENU_SQ_MUSIC(GarminVenuSqMusicCoordinator.class),
    GARMIN_VENU_SQ_2(GarminVenuSq2Coordinator.class),
    GARMIN_VENU_SQ_2_MUSIC(GarminVenuSq2MusicCoordinator.class),
    GARMIN_VENU_2(GarminVenu2Coordinator.class),
    GARMIN_VENU_2S(GarminVenu2SCoordinator.class),
    GARMIN_VENU_2_PLUS(GarminVenu2PlusCoordinator.class),
    GARMIN_VENU_3(GarminVenu3Coordinator.class),
    GARMIN_VENU_3S(GarminVenu3SCoordinator.class),
    GARMIN_VENU_4(GarminVenu4Coordinator.class),
    GARMIN_VENU_X1(GarminVenuX1Coordinator.class),
    GARMIN_VIVOACTIVE(GarminVivoActiveCoordinator.class),
    GARMIN_VIVOACTIVE_3(GarminVivoActive3Coordinator.class),
    GARMIN_VIVOACTIVE_3_MUSIC(GarminVivoActive3MusicCoordinator.class),
    GARMIN_VIVOACTIVE_3T(GarminVivoActive3TCoordinator.class),
    GARMIN_VIVOACTIVE_4(GarminVivoActive4Coordinator.class),
    GARMIN_VIVOACTIVE_4S(GarminVivoActive4SCoordinator.class),
    GARMIN_VIVOACTIVE_5(GarminVivoActive5Coordinator.class),
    GARMIN_VIVOACTIVE_6(GarminVivoActive6Coordinator.class),
    GARMIN_VIVOACTIVE_HR(GarminVivoActiveHrCoordinator.class),
    GARMIN_VIVOSMART(GarminVivosmartCoordinator.class),
    GARMIN_VIVOSMART_HR(GarminVivosmartHrCoordinator.class),
    GARMIN_VIVOSMART_HR_PLUS(GarminVivosmartHrPlusCoordinator.class),
    GARMIN_VIVOSMART_3(GarminVivosmart3Coordinator.class),
    GARMIN_VIVOSMART_4(GarminVivosmart4Coordinator.class),
    GARMIN_VIVOSMART_5(GarminVivosmart5Coordinator.class),
    GARMIN_VIVOSPORT(GarminVivosportCoordinator.class),
    GARMIN_CIRQA(GarminCirqaCoordinator.class),
    GARMIN_D2_AIR(GarminD2AirCoordinator.class),
    GARMIN_D2_AIR_X10(GarminD2AirX10Coordinator.class),
    GARMIN_D2_AIR_X15(GarminD2AirX15Coordinator.class),
    GARMIN_D2_MACH_1(GarminD2Mach1Coordinator.class),
    GARMIN_D2_MACH_1_PRO(GarminD2Mach1ProCoordinator.class),
    GARMIN_D2_MACH_2(GarminD2Mach2Coordinator.class),
    GARMIN_D2_MACH_2_PRO(GarminD2Mach2ProCoordinator.class),
    GREE_AC(GreeAcCoordinator.class),
    AAWIRELESS(AAWirelessCoordinator.class),
    VIBRATISSIMO(VibratissimoCoordinator.class),
    SONY_SWR12(SonySWR12DeviceCoordinator.class),
    LIVEVIEW(LiveviewCoordinator.class),
    WASPOS(WaspOSCoordinator.class),
    UM25(UM25Coordinator.class),
    DOMYOS_T540(DomyosT540Coordinator.class),
    CONCEPT2_PM5(Concept2PM5Coordinator.class),
    EVEN_REALITIES_G_1(G1DeviceCoordinator.class),
    NOTHING_EAR1(Ear1Coordinator.class),
    NOTHING_EAR2(Ear2Coordinator.class),
    NOTHING_EAR_A(EarACoordinator.class),
    NOTHING_EAR_STICK(EarStickCoordinator.class),
    NOTHING_CMF_BUDS_2(CmfBuds2Coordinator.class),
    NOTHING_CMF_BUDS_2A(CmfBuds2aCoordinator.class),
    NOTHING_CMF_BUDS_PRO_2(CmfBudsPro2Coordinator.class),
    NOTHING_CMF_BUDS_2_PLUS(CmfBuds2PlusCoordinator.class),
    NOTHING_CMF_WATCH_PRO(CmfWatchProCoordinator.class),
    NOTHING_CMF_WATCH_PRO_2(CmfWatchPro2Coordinator.class),
    NOTHING_CMF_WATCH_PRO_3(CmfWatchPro3Coordinator.class),
    GALAXY_BUDS_PRO(GalaxyBudsProDeviceCoordinator.class),
    GALAXY_BUDS_LIVE(GalaxyBudsLiveDeviceCoordinator.class),
    GALAXY_BUDS(GalaxyBudsDeviceCoordinator.class),
    GALAXY_BUDS2(GalaxyBuds2DeviceCoordinator.class),
    GALAXY_BUDS2_PRO(GalaxyBuds2ProDeviceCoordinator.class),
    GALAXY_BUDS3_PRO(GalaxyBuds3ProDeviceCoordinator.class),
    SONY_WH_1000XM3(SonyWH1000XM3Coordinator.class),
    SONY_WF_SP800N(SonyWFSP800NCoordinator.class),
    SONY_WI_C100(SonyWIC100Coordinator.class),
    SONY_WI_SP600N(SonyWISP600NCoordinator.class),
    SONY_WH_1000XM4(SonyWH1000XM4Coordinator.class),
    SONY_WF_1000XM3(SonyWF1000XM3Coordinator.class),
    SONY_WH_1000XM2(SonyWH1000XM2Coordinator.class),
    SONY_WF_1000XM4(SonyWF1000XM4Coordinator.class),
    SONY_LINKBUDS(SonyLinkBudsCoordinator.class),
    SONY_LINKBUDS_S(SonyLinkBudsSCoordinator.class),
    SONY_WH_1000XM5(SonyWH1000XM5Coordinator.class),
    SONY_WH_1000XM6(SonyWH1000XM6Coordinator.class),
    SONY_WH_ULT900N(SonyWHULT900NCoordinator.class),
    SONY_WF_1000XM5(SonyWF1000XM5Coordinator.class),
    SONY_WF_C500(SonyWFC500Coordinator.class),
    SONY_WF_C510(SonyWFC510Coordinator.class),
    SONY_WF_C700N(SonyWFC700NCoordinator.class),
    SONY_WF_C710N(SonyWFC710NCoordinator.class),
    SONY_WH_CH720N(SonyWHCH720NCoordinator.class),
    SONY_REON_POCKET_PRO(SonyReonPocketProCoordinator.class),
    PIXEL_BUDS_A(PixelBudsACoordinator.class),
    SHOKZ_OPENSWIM_PRO(ShokzOpenSwimProCoordinator.class),
    SHOKZ_OPENRUN_PRO_2(ShokzOpenRunPro2Coordinator.class),
    BM2_BATTERY_MONITOR(Bm2Coordinator.class),
    BM6_BATTERY_MONITOR(Bm6Coordinator.class),
    VICTRON_SMARTSHUNT(VictronSmartShuntCoordinator.class),
    DJI_OSMO_ACTION_4(DjiOsmoAction4Coordinator.class),
    SINILINK(SinilinkCoordinator.class),
    ONETOUCH(OneTouchCoordinator.class),
    SOUNDBRENNER_CORE(SoundbrennerCoordinator.class),
    SOUNDCORE_LIBERTY3_PRO(SoundcoreLiberty3ProCoordinator.class),
    SOUNDCORE_LIBERTY4_NC(SoundcoreLiberty4NCCoordinator.class),
    SOUNDCORE_MOTION300(SoundcoreMotion300Coordinator.class),
    SOUNDCORE_MOTION_PLUS(SoundcoreMotionPlusCoordinator.class),
    SOUNDCORE_Q30(SoundcoreQ30Coordinator.class),
    SOUNDCORE_SPACE_ONE(SoundcoreSpaceOneCoordinator.class),
    SOUNDCORE_LIFE_TUNE(SoundcoreLifeTuneCoordinator.class),
    SOUNDCORE_LIFE_TUNE_XR(SoundcoreLifeTuneXRCoordinator.class),
    SOUNDCORE_AEROFIT2(SoundcoreAeroFit2Coordinator.class),
    SOUNDCORE_SPORT_X20(SoundcoreSportX20Coordinator.class),
    SOUNDCORE_A1(SoundcoreA1Coordinator.class),
    SOUNDCORE_A2_NC(SoundcoreA2NCCoordinator.class),
    SOUNDCORE_A3I(SoundcoreA3iCoordinator.class),
    SOUNDCORE_A30I(SoundcoreA30iCoordinator.class),
    SOUNDCORE_K20I(SoundcoreK20iCoordinator.class),
    SOUNDCORE_P2(SoundcoreP2Coordinator.class),
    SOUNDCORE_P2I(SoundcoreP2iCoordinator.class),
    SOUNDCORE_P2_MINI(SoundcoreP2MiniCoordinator.class),
    SOUNDCORE_P3(SoundcoreP3Coordinator.class),
    SOUNDCORE_P3I(SoundcoreP3iCoordinator.class),
    SOUNDCORE_P20I(SoundcoreP20iCoordinator.class),
    SOUNDCORE_P30I(SoundcoreP30iCoordinator.class),
    SOUNDCORE_P31I(SoundcoreP31iCoordinator.class),
    SOUNDCORE_P40I(SoundcoreP40iCoordinator.class),
    SOUNDCORE_P41I(SoundcoreP41iCoordinator.class),
    SOUNDCORE_P42I(SoundcoreP42iCoordinator.class),
    SOUNDCORE_V20I(SoundcoreV20iCoordinator.class),
    HAYLOU_S35_ANC(HaylouS35AncCoordinator.class),
    MOONDROP_SPACE_TRAVEL(MoondropSpaceTravelCoordinator.class),
    MOONDROP_SPACE_TRAVEL_2(MoondropSpaceTravel2Coordinator.class),
    MOONDROP_SPACE_TRAVEL_2_ULTRA(MoondropSpaceTravel2UltraCoordinator.class),
    BOSE_QC35(QC35Coordinator.class),
    BOSE_NC700(NC700Coordinator.class),
    JABRA_EVOLVE_65(JabraEvolve65Coordinator.class),
    JABRA_EVOLVE2_55(JabraEvolve255Coordinator.class),
    JABRA_EVOLVE2_65(JabraEvolve265Coordinator.class),
    ONEMORE_SONOFLOW(OneMoreSonoFlowCoordinator.class),
    ONEMORE_SONOFLOW_SE(OneMoreSonoFlowSECoordinator.class),
    UGREEN_HITUNE_MAX_5C(UgreenHituneMax5cCoordinator.class),
    HONORBAND3(HonorBand3Coordinator.class),
    HONORBAND4(HonorBand4Coordinator.class),
    HONORBAND5(HonorBand5Coordinator.class),
    HUAWEIBANDAW70(HuaweiBandAw70Coordinator.class),
    HUAWEIBAND6(HuaweiBand6Coordinator.class),
    HUAWEIWATCHGT(HuaweiWatchGTCoordinator.class),
    HUAWEIBAND2PRO(HuaweiBand2ProCoordinator.class),
    HUAWEIBAND3PRO(HuaweiBand3ProCoordinator.class),
    HUAWEIBAND4PRO(HuaweiBand4ProCoordinator.class),
    HUAWEIWATCHGT2(HuaweiWatchGT2Coordinator.class),
    HUAWEIWATCHGT2E(HuaweiWatchGT2eCoordinator.class),
    HUAWEITALKBANDB6(HuaweiTalkBandB6Coordinator.class),
    HUAWEIBAND7(HuaweiBand7Coordinator.class),
    HONORBAND6(HonorBand6Coordinator.class),
    HONORBAND7(HonorBand7Coordinator.class),
    HONORBAND9(HonorBand9Coordinator.class),
    HONORBAND10(HonorBand10Coordinator.class),
    HONORWATCH4(HonorWatch4Coordinator.class),
    HONORWATCH5(HonorWatch5Coordinator.class),
    HONORWATCH5PRO(HonorWatch5ProCoordinator.class),
    HONORWATCH6(HonorWatch6Coordinator.class),
    HONORMAGICWATCH(HonorMagicWatchCoordinator.class),
    HONORMAGICWATCH2(HonorMagicWatch2Coordinator.class),
    HONORWATCHGS3(HonorWatchGS3Coordinator.class),
    HONORWATCHGSPRO(HonorWatchGSProCoordinator.class),
    HUAWEIWATCHD2(HuaweiWatchD2Coordinator.class),
    HUAWEIWATCHD3(HuaweiWatchD3Coordinator.class),
    HUAWEIWATCHGT3(HuaweiWatchGT3Coordinator.class),
    HUAWEIWATCHGT4(HuaweiWatchGT4Coordinator.class),
    HUAWEIWATCHGT5(HuaweiWatchGT5Coordinator.class),
    HUAWEIWATCHGT6(HuaweiWatchGT6Coordinator.class),
    HUAWEIWATCHGT7(HuaweiWatchGT7Coordinator.class),
    HUAWEIWATCHGT7PRO(HuaweiWatchGT7ProCoordinator.class),
    HUAWEIWATCHGTRUNNER(HuaweiWatchGTRunnerCoordinator.class),
    HUAWEIWATCHGTRUNNER2(HuaweiWatchGTRunner2Coordinator.class),
    HUAWEIWATCHGTCYBER(HuaweiWatchGTCyberCoordinator.class),
    HUAWEIBAND8(HuaweiBand8Coordinator.class),
    HUAWEIBAND9(HuaweiBand9Coordinator.class),
    HUAWEIBAND10(HuaweiBand10Coordinator.class),
    HUAWEIBAND11(HuaweiBand11Coordinator.class),
    HUAWEIBAND11PRO(HuaweiBand11ProCoordinator.class),
    HUAWEIWATCHFIT(HuaweiWatchFitCoordinator.class),
    HUAWEIWATCHFIT2(HuaweiWatchFit2Coordinator.class),
    HUAWEIWATCHFIT3(HuaweiWatchFit3Coordinator.class),
    HUAWEIWATCHFIT4PRO(HuaweiWatchFit4ProCoordinator.class),
    HUAWEIWATCHFIT5(HuaweiWatchFit5Coordinator.class),
    HUAWEIWATCHFIT5PRO(HuaweiWatchFit5ProCoordinator.class),
    HUAWEIWATCHULTIMATE(HuaweiWatchUltimateCoordinator.class),
    HUAWEIWATCHULTIMATE2(HuaweiWatchUltimate2Coordinator.class),
    HUAWEIWATCH3(HuaweiWatch3Coordinator.class),
    HUAWEIWATCH4PRO(HuaweiWatch4ProCoordinator.class),
    HUAWEIWATCH5(HuaweiWatch5Coordinator.class),
    HUAWEIWATCH6(HuaweiWatch6Coordinator.class),
    HUAWEIWATCH6PRO(HuaweiWatch6ProCoordinator.class),
    HUAWEI_FREEARC(HuaweiFreearcCoordinator.class),
    HUAWEI_FREECLIP2(HuaweiFreeClip2Coordinator.class),
    HUAWEI_FREEBUDS5I(HuaweiFreebuds5iCoordinator.class),
    HUAWEI_FREEBUDS_PRO(HuaweiFreebudsProCoordinator.class),
    HUAWEI_FREEBUDS6(HuaweiFreebuds6Coordinator.class),
    VESC(VescCoordinator.class),
    BINARY_SENSOR(BinarySensorCoordinator.class),
    FLIPPER_ZERO(FlipperZeroCoordinator.class),
    SUPER_CARS(SuperCarsCoordinator.class),
    ASTEROIDOS(AsteroidOSDeviceCoordinator.class),
    OPPO_ENCO_AIR(OppoEncoAirCoordinator.class),
    OPPO_ENCO_AIR2(OppoEncoAir2Coordinator.class),
    OPPO_ENCO_BUDS2(OppoEncoBuds2Coordinator.class),
    OUKITEL_BT103(OukitelBT103Coordinator.class),
    GLORYFIT_SWISS_PEAK(SwissPeakCoordinator.class),
    BLACKVIEW_R3MAX(BlackviewR3MaxCoordinator.class),
    DOTN_P66D(DotnP66DCoordinator.class),
    QECNATO_S10(QecnatoS10Coordinator.class),
    R1(R1Coordinator.class),
    GRV_FC1(GrvFc1Coordinator.class),
    IMIKI_FRAME_2(ImikiFrame2Coordinator.class),
    KIESLECT_CALLING_SMARTWATCH_KR_PRO(KieslectCallingSmartwatchKrProCoordinator.class),
    HAYLOU_WATCH_2_PRO(HaylouWatch2ProCoordinator.class),
    S52(S52Coordinator.class),
    HIFUTURE_ZONE2(HiFutureZone2Coordinator.class),
    D3(D3Coordinator.class),
    Y6(Y6Coordinator.class),
    Y66(Y66Coordinator.class),
    T70(T70Coordinator.class),
    DM58(DM58Coordinator.class),
    REALME_BUDS_T110(RealmeBudsT110Coordinator.class),
    REALME_BUDS_T100(RealmeBudsT100Coordinator.class),
    REALME_BUDS_T200(RealmeBudsT200Coordinator.class),
    REALME_BUDS_T300(RealmeBudsT300Coordinator.class),
    REALME_BUDS_AIR_5_PRO(RealmeBudsAir5ProCoordinator.class),
    REALME_BUDS_AIR_6_PRO(RealmeBudsAir6ProCoordinator.class),
    SOFLOW_SO6(SoFlowCoordinator.class),
    XIAOMI_SCOOTER_5_MAX(XiaomiScooter5MaxCoordinator.class),
    NINEBOT_KICKSCOOTER(NinebotScooterCoordinator.class),
    XIAOMI_HIPEE_P1(XiaomiHipeeP1Coordinator.class),
    WITHINGS_STEEL_HR(WithingsSteelHRDeviceCoordinator.class),
    SONY_WENA_3(SonyWena3Coordinator.class),
    FEMOMETER_VINCA2(FemometerVinca2DeviceCoordinator.class),
    PIXOO(PixooCoordinator.class),
    WEARFIT_HK8_PRO(WearFitCoordinator.class),
    HAMA_FIT6900(HamaFit6900DeviceCoordinator.class),
    XPLORA_XMOVE(XploraXmoveDeviceCoordinator.class),
    YAWELL_R05(YawellR05Coordinator.class),
    YAWELL_R10(YawellR10Coordinator.class),
    YAWELL_R11(YawellR11Coordinator.class),
    YAWELL_Y27(YawellY27Coordinator.class),
    COLMI_R02(ColmiR02Coordinator.class),
    COLMI_R03(ColmiR03Coordinator.class),
    COLMI_R06(ColmiR06Coordinator.class),
    COLMI_R07(ColmiR07Coordinator.class),
    COLMI_R09(ColmiR09Coordinator.class),
    COLMI_R10(ColmiR10Coordinator.class),
    COLMI_R12(ColmiR12Coordinator.class),
    H59(H59Coordinator.class),
    COLMI_I28_ULTRA(ColmiI28UltraCoordinator.class),
    COLMI_I31(ColmiI31Coordinator.class),
    COLMI_P10(ColmiP10Coordinator.class),
    COLMI_P76(ColmiP76Coordinator.class),
    COLMI_P80(ColmiP80Coordinator.class),
    COLMI_P81(ColmiP81Coordinator.class),
    COLMI_V72(ColmiV72Coordinator.class),
    COLMI_V73(ColmiV73Coordinator.class),
    COLMI_V76(ColmiV76Coordinator.class),
    COLMI_V89(ColmiV89Coordinator.class),
    ALT_VIBE(AltVibeCoordinator.class),
    BLACKVIEW_R60(BlackviewR60Coordinator.class),
    MISIRUN_C17(MisirunC17Coordinator.class),
    C20(C20Coordinator.class),
    C20PRO(C20ProCoordinator.class),
    L70(L70Coordinator.class),
    HC96(HC96Coordinator.class),
    KW19_B(KW19BCoordinator.class),
    KT80(KT80Coordinator.class),
    ITECH_ACTIVE_3(ITechActive3Coordinator.class),
    KSIX_VENTURE(KsixVentureCoordinator.class),
    FIREBOLTT_069(FireBoltt069Coordinator.class),
    FIREBOLTT_TALK(FireBolttTalkCoordinator.class),
    FIREBOLTT_NINJA_PHANTOM(FireBolttNinjaPhantomCoordinator.class),
    VIRAN_C29(ViranC29Coordinator.class),
    ZL02D(ZL02DCoordinator.class),
    R50PRO(R50ProCoordinator.class),
    RAINBUVVY_T97(RainbuvvyT97Coordinator.class),
    MT55(MT55Coordinator.class),
    GARETT_EVA(GarettEvaCoordinator.class),
    ADVAN_WATCH_SE1_AI(AdvanWatchSE1AICoordinator.class),
    BETTDOW_FB051(BettdowFB051Coordinator.class),
    B_AND_W_P_SERIES(BandWPSeriesDeviceCoordinator.class),
    EARFUN_AIR_S(EarFunAirSCoordinator.class),
    EARFUN_AIR_PRO_4(EarFunAirPro4Coordinator.class),
    EARFUN_FREE_PRO_3(EarFunFreePro3Coordinator.class),
    SENNHEISER_MOMENTUM_IN_EAR_WIRELESS(SennheiserMomentumInEarWirelessCoordinator.class),
    SCANNABLE(ScannableDeviceCoordinator.class),
    CYCLING_SENSOR(CyclingSensorCoordinator.class),
    BLE_GATT_CLIENT(BleGattClientCoordinator.class),
    MARSTEK_B2500(MarstekB2500DeviceCoordinator.class),
    GENERIC_HEADPHONES(GenericHeadphonesCoordinator.class),
    GENERIC_THERMAL_PRINTER(ThermalPrinterCoordinator.class),
    GENERIC_HEART_RATE(GenericHeartRateCoordinator.class),
    ULTRAHUMAN_RING_AIR(UltrahumanDeviceCoordinator.class),
    WHOOP(app.superhealth.engine.devices.whoop.WhoopCoordinator.class),
    ATC_BLE_OEPL(ATCBLEOEPLCoordinator.class),
    COOSPO_H6(CoospoH6Coordinator.class),
    COOSPO_HW9(CoospoHW9Coordinator.class),
    COOSPO_HW807(CoospoHW807Coordinator.class),
    COOSPO_H9Z(CoospoH9ZCoordinator.class),
    KEEPHEALTH_C60(C60DeviceCoordinator.class),
    GENERIC_WEIGHT_SCALE(GenericWeightScaleCoordinator.class),
    SOLARFLOW(SolarFlowDeviceCoordinator.class),
    SANITAS_SBM_67(SanitasSBM67Coordinator.class),
    SILVERCREST_SBM_67(SilverCrestSBM67Coordinator.class),
    BEURER_BM_59(BeurerBm59Coordinator.class),
    BEURER_BM_69(BeurerBm69Coordinator.class),
    BRAUN_BPW4500(BraunBPW4500DeviceCoordinator.class),
    TEUFEL_MYND(TeufelMyndCoordinator.class),
    IGPSPORT_BSC100MAX(IGPSportBSC100MaxCoordinator.class),
    IGPSPORT_BSC200(IGPSportBSC200Coordinator.class),
    IGPSPORT_BSC200S(IGPSportBSC200SCoordinator.class),
    IGPSPORT_BSC300(IGPSportBSC300Coordinator.class),
    IGPSPORT_BSC500(IGPSportBSC500Coordinator.class),
    IGPSPORT_IGS630(IGPSportiGS630Coordinator.class),
    IGPSPORT_IGS630S(IGPSportiGS630SCoordinator.class),
    IGPSPORT_IGS800(IGPSportiGS800Coordinator.class),
    IGPSPORT_BINAVI_AIR(IGPSportBiNaviAirCoordinator.class),
    IGPSPORT_BINAVI(IGPSportBiNaviCoordinator.class),
    OLLEE_WATCH_ONE(OlleeDeviceCoordinator.class),
    UNA_WATCH(UnaDeviceCoordinator.class),
    COROS_PACE_3(CorosPace3Coordinator.class),
    ZEBLAZE_BEYOND_3_PRO(Beyond3ProCoordinator.class),
    ZEBLAZE_STRATOS_4_PRO(Stratos4ProCoordinator.class),
    QN_SCALE(QnScaleCoordinator.class),
    CARDO_LS24X(Ls24xDeviceCoordinator.class),
    F8SCALE(F8ScaleCoordinator.class),
    EIGHTBITDO_MICRO(EightBitDoMicroCoordinator.class),
    INNOVO_IP900BP_B(InnovoPulseOximeterCoordinator.class),
    TEST(TestDeviceCoordinator.class);

    private DeviceCoordinator coordinator;

    private final Class<? extends DeviceCoordinator> coordinatorClass;

    DeviceType(final Class<? extends DeviceCoordinator> coordinatorClass) {
        this.coordinatorClass = coordinatorClass;
    }

    public boolean isSupported() {
        return this != UNKNOWN;
    }

    public static DeviceType fromName(final String name) {
        for (DeviceType type : values()) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        return DeviceType.UNKNOWN;
    }

    public Class<? extends DeviceCoordinator> getCoordinatorClass() {
        return coordinatorClass;
    }

    public DeviceCoordinator getDeviceCoordinator() {
        if (coordinator == null) {
            try {
                final Constructor<? extends DeviceCoordinator> constructor = coordinatorClass.getConstructor();
                coordinator = constructor.newInstance();
            } catch (final ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
        return coordinator;
    }
}
