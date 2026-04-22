package me.bounser.nascraft.database.commands;

import me.bounser.nascraft.support.DatabaseTest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebtDaoTest extends DatabaseTest {

    private static final double DELTA = 0.0001;

    @Test
    void increaseDebt_insertsOnFirstCallAndAccumulatesOnSubsequent() {
        UUID uuid = UUID.randomUUID();

        Debt.increaseDebt(connection, uuid, 100.0);
        Debt.increaseDebt(connection, uuid, 50.25);

        assertEquals(150.25, Debt.getDebt(connection, uuid), DELTA);
    }

    @Test
    void getDebt_returnsZeroForUnknownPlayer() {
        assertEquals(0.0, Debt.getDebt(connection, UUID.randomUUID()), DELTA);
    }

    @Test
    void decreaseDebt_subtractsPartialRepayment() {
        UUID uuid = UUID.randomUUID();
        Debt.increaseDebt(connection, uuid, 100.0);

        Debt.decreaseDebt(connection, uuid, 30.0);

        assertEquals(70.0, Debt.getDebt(connection, uuid), DELTA);
    }

    @Test
    void decreaseDebt_removesRowWhenFullyRepaid() {
        UUID uuid = UUID.randomUUID();
        Debt.increaseDebt(connection, uuid, 100.0);

        Debt.decreaseDebt(connection, uuid, 100.0);

        assertEquals(0.0, Debt.getDebt(connection, uuid), DELTA);
        assertFalse(Debt.getUUIDAndDebt(connection).containsKey(uuid),
            "fully repaid debts must not appear in the outstanding-debt map");
    }

    @Test
    void getUUIDAndDebt_returnsOnlyDebtorsWithPositiveBalance() {
        UUID debtor = UUID.randomUUID();
        UUID repaid = UUID.randomUUID();

        Debt.increaseDebt(connection, debtor, 40.0);
        Debt.increaseDebt(connection, repaid, 10.0);
        Debt.decreaseDebt(connection, repaid, 10.0);

        HashMap<UUID, Double> outstanding = Debt.getUUIDAndDebt(connection);

        assertEquals(1, outstanding.size());
        assertTrue(outstanding.containsKey(debtor));
        assertEquals(40.0, outstanding.get(debtor), DELTA);
    }

    @Test
    void addInterestPaid_accumulatesAcrossCalls() {
        UUID uuid = UUID.randomUUID();

        Debt.addInterestPaid(connection, uuid, 5.0);
        Debt.addInterestPaid(connection, uuid, 2.5);

        assertEquals(7.5, Debt.getInterestsPaid(connection, uuid), DELTA);
    }

    @Test
    void getAllOutstandingDebt_sumsAcrossAllDebtors() {
        Debt.increaseDebt(connection, UUID.randomUUID(), 10.0);
        Debt.increaseDebt(connection, UUID.randomUUID(), 25.5);
        Debt.increaseDebt(connection, UUID.randomUUID(), 4.5);

        assertEquals(40.0, Debt.getAllOutstandingDebt(connection), DELTA);
    }

    @Test
    void getAllInterestsPaid_sumsAcrossAllPayers() {
        Debt.addInterestPaid(connection, UUID.randomUUID(), 3.0);
        Debt.addInterestPaid(connection, UUID.randomUUID(), 7.0);

        assertEquals(10.0, Debt.getAllInterestsPaid(connection), DELTA);
    }
}
