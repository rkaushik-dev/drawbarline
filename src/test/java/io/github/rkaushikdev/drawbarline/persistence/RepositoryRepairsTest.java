package io.github.rkaushikdev.drawbarline.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.BillingError;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.persistence.models.DepreciationRow;
import io.github.rkaushikdev.drawbarline.persistence.models.PriceLineRow;
import io.github.rkaushikdev.drawbarline.repairs.Defect;
import io.github.rkaushikdev.drawbarline.repairs.Depreciation;
import io.github.rkaushikdev.drawbarline.repairs.JobCode;
import io.github.rkaushikdev.drawbarline.repairs.LaborRate;
import io.github.rkaushikdev.drawbarline.repairs.LaborRates;
import io.github.rkaushikdev.drawbarline.repairs.PriceLine;
import io.github.rkaushikdev.drawbarline.repairs.PriceMaster;
import io.github.rkaushikdev.drawbarline.repairs.PricedCard;
import io.github.rkaushikdev.drawbarline.repairs.RepairCard;
import io.github.rkaushikdev.drawbarline.repairs.WhyMade;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RepositoryRepairsTest {

    private static final JobCode WHEELSET = new JobCode("4312");
    private static final JobCode COUPLER = new JobCode("5104");
    private static final ReportingMark CN = new ReportingMark("CN");
    private static final ReportingMark ELS = new ReportingMark("ELS");
    private static final LocalDate JULY_2024 = LocalDate.of(2024, 7, 1);
    private static final LocalDate MID_JANUARY = LocalDate.of(2025, 1, 14);

    private static Database empty() {
        return TestDatabase.fresh();
    }

    private static Database filed() {
        return TestDatabase.seeded(work -> {
            work.repairPricing().addPrice(new PriceLine(WHEELSET, JULY_2024, 250, 128000));
            work.repairPricing().addPrice(new PriceLine(WHEELSET, LocalDate.of(2025, 7, 1), 250, 139000));
            work.repairPricing().addPrice(new PriceLine(COUPLER, JULY_2024, 125, 84000));
            work.repairPricing().setDepreciation(new Depreciation("43", 4, 20));
            work.repairPricing().fileLabor(new LaborRate(CN, JULY_2024, 4200));
            work.repairPricing().fileLabor(new LaborRate(ELS, JULY_2024, 3900));
        });
    }

    private static PriceMaster master(Database books) {
        try (UnitOfWork work = books.unitOfWork()) {
            return work.repairPricing().master();
        }
    }

    @Test
    void theRepairTablesAreOnTheMetadata() {
        assertThat(Database.modelTables()).contains("price_line", "depreciation", "labor_rate");
    }

    @Test
    void aPriceRowReprNamesTheJobAndDate() {
        assertThat(new PriceLineRow("4312", JULY_2024, 1, 1)).hasToString("<PriceLineRow 4312 from 2024-07-01>");
    }

    @Test
    void theMasterLoadsBack() {
        PriceMaster master = master(filed());
        assertThat(master.lineFor(WHEELSET, MID_JANUARY).material()).isEqualTo(new Money(128000));
        assertThat(master.lineFor(WHEELSET, LocalDate.of(2025, 7, 1)).material())
                .isEqualTo(new Money(139000));
    }

    @Test
    void theDepreciationRulesLoadWithIt() {
        PriceMaster master = master(filed());
        assertThat(master.materialFor(WHEELSET, MID_JANUARY, 5)).isEqualTo(new Money(102400));
        assertThat(master.materialFor(COUPLER, MID_JANUARY, 20)).isEqualTo(new Money(84000));
    }

    @Test
    void pricingAJobTwiceFromOneDateIsRefused() {
        try (UnitOfWork work = filed().unitOfWork()) {
            assertThatThrownBy(() -> work.repairPricing().addPrice(new PriceLine(WHEELSET, JULY_2024, 1, 1)))
                    .isInstanceOf(BillingError.class);
        }
    }

    @Test
    void aDepreciationRuleIsReplacedInPlace() {
        Database books = filed();
        try (UnitOfWork work = books.unitOfWork()) {
            work.repairPricing().setDepreciation(new Depreciation("43", 6, 10));
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            PriceMaster master = work.repairPricing().master();
            assertThat(master.depreciationFor(WHEELSET).orElseThrow().percentAYear())
                    .isEqualTo(6);
            List<DepreciationRow> rows = work.session()
                    .createQuery("select d from DepreciationRow d", DepreciationRow.class)
                    .getResultList();
            assertThat(rows).hasSize(1);
        }
    }

    @Test
    void aPercentageOverAHundredIsRefusedByTheTable() {
        try (UnitOfWork work = empty().unitOfWork()) {
            assertThatThrownBy(() -> {
                        work.session().persist(new DepreciationRow("43", 140, 20));
                        work.commit();
                    })
                    .isInstanceOf(PersistenceException.class);
        }
    }

    @Test
    void aNegativeMaterialPriceIsRefusedByTheTable() {
        try (UnitOfWork work = empty().unitOfWork()) {
            assertThatThrownBy(() -> {
                        work.session().persist(new PriceLineRow("4312", JULY_2024, 1, -1));
                        work.commit();
                    })
                    .isInstanceOf(PersistenceException.class);
        }
    }

    @Test
    void theLaborRatesLoadBack() {
        LaborRates rates;
        try (UnitOfWork work = filed().unitOfWork()) {
            rates = work.repairPricing().laborRates();
        }
        assertThat(rates.rateFor(CN, MID_JANUARY).centsAnHour()).isEqualTo(4200);
        assertThat(rates.roads()).extracting(ReportingMark::toString).containsExactly("CN", "ELS");
    }

    @Test
    void aRateCanBeAskedForDirectly() {
        try (UnitOfWork work = filed().unitOfWork()) {
            assertThat(work.repairPricing().rateFor(ELS, MID_JANUARY).centsAnHour())
                    .isEqualTo(3900);
        }
    }

    @Test
    void aRoadFilingTwiceOnOneDayIsRefused() {
        try (UnitOfWork work = filed().unitOfWork()) {
            assertThatThrownBy(() -> work.repairPricing().fileLabor(new LaborRate(CN, JULY_2024, 4300)))
                    .isInstanceOf(BillingError.class);
        }
    }

    @Test
    void aLaterRateTakesOver() {
        Database books = filed();
        try (UnitOfWork work = books.unitOfWork()) {
            work.repairPricing().fileLabor(new LaborRate(CN, LocalDate.of(2025, 7, 1), 4550));
            work.commit();
        }
        LaborRates rates;
        try (UnitOfWork work = books.unitOfWork()) {
            rates = work.repairPricing().laborRates();
        }
        assertThat(rates.rateFor(CN, LocalDate.of(2025, 7, 1)).centsAnHour()).isEqualTo(4550);
    }

    @Test
    void aCardPricesTheSameWayOutOfTheDatabase() {
        RepairCard card = new RepairCard(
                88104,
                CarMark.parse("DBL 104207"),
                CN,
                MID_JANUARY,
                "ESC",
                List.of(new Defect(WHEELSET, new WhyMade("01")).withPartYears(5)));
        PricedCard priced;
        try (UnitOfWork work = filed().unitOfWork()) {
            priced = PricedCard.price(
                    card, work.repairPricing().master(), work.repairPricing().laborRates());
        }
        assertThat(priced.total()).isEqualTo(new Money(10500 + 102400));
    }

    @Test
    void theRepositoryCountsItsPriceLines() {
        try (UnitOfWork work = filed().unitOfWork()) {
            assertThat(work.repairPricing().size()).isEqualTo(3);
        }
    }

    @Test
    void anEmptyMasterPricesNothing() {
        PriceMaster master = master(empty());
        assertThatThrownBy(() -> master.lineFor(WHEELSET, MID_JANUARY)).isInstanceOf(BillingError.class);
    }
}
