import Foundation
import KilkariCore

/// The fund's arithmetic, which is the part of the money screen that can be wrong quietly.
enum FundMathChecks {

    private static let cal = Calendar(identifier: .gregorian)
    private static let epoch = Date(timeIntervalSince1970: 1_700_000_000)

    private static func day(_ offset: Int) -> Date {
        cal.date(byAdding: .day, value: offset, to: epoch)!
    }

    private static let savings = "a"
    private static let cash = "b"

    private static let deposit = FundMath.Movement(id: "m1", date: day(-30), amount: 15_000,
                                                   note: "Monthly", accountId: savings)
    private static let withdrawal = FundMath.Movement(id: "m2", date: day(-20), amount: -2_000,
                                                      note: "Emergency", accountId: savings)
    private static let nappies = FundMath.Outgoing(id: "e1", date: day(-10), title: "Nappies",
                                                   subtitle: "General", amount: 1_899,
                                                   accountId: savings)
    private static let sip = FundMath.Outgoing(id: "c1", date: day(-5), title: "Sukanya",
                                               amount: 1_000, accountId: cash, investment: true)
    private static let outLeg = FundMath.Movement(id: "m3", date: day(-3), amount: -3_000,
                                                  accountId: savings, group: "g1")
    private static let inLeg = FundMath.Movement(id: "m4", date: day(-3), amount: 3_000,
                                                 accountId: cash, group: "g1")

    static func run() {
        Check.group("the balance is what went in, less everything that went out") {
            Check.equal(FundMath.total(movements: [deposit, withdrawal], outgoings: [nappies, sip]),
                        15_000 - 2_000 - 1_899 - 1_000, "total")
        }

        Check.group("spending more than is there goes negative rather than to zero") {
            Check.equal(FundMath.total(movements: [], outgoings: [nappies]), -1_899, "total")
        }

        Check.group("a transfer leaves the total exactly where it was") {
            Check.equal(FundMath.total(movements: [deposit, withdrawal, outLeg, inLeg], outgoings: []),
                        FundMath.total(movements: [deposit, withdrawal], outgoings: []), "total")
        }

        Check.group("a transfer moves the two accounts, and they still add up to the fund") {
            let movements = [deposit, withdrawal, outLeg, inLeg]
            let outgoings = [nappies, sip]
            let balances = FundMath.balances(movements: movements, outgoings: outgoings)
            Check.equal(balances[savings], 15_000 - 2_000 - 3_000 - 1_899, "the one it left")
            Check.equal(balances[cash], 3_000 - 1_000, "the one it arrived in")
            Check.equal(balances.values.reduce(0, +),
                        FundMath.total(movements: movements, outgoings: outgoings), "the sum")
        }

        Check.group("money in no account, and money in one since removed, are both unassigned") {
            let loose = FundMath.Movement(id: "m5", date: day(-40), amount: 500)
            let orphan = FundMath.Movement(id: "m6", date: day(-40), amount: 700, accountId: "gone")
            Check.equal(FundMath.unassigned(movements: [deposit, loose, orphan], outgoings: [],
                                            accountIds: [savings, cash]), 1_200, "loose and orphaned")
            Check.equal(FundMath.unassigned(movements: [deposit], outgoings: [],
                                            accountIds: [savings, cash]), 0, "nothing loose")
        }

        Check.group("every movement and every outgoing reaches the ledger once, newest first") {
            let ledger = FundMath.ledger(movements: [deposit, withdrawal, outLeg, inLeg],
                                         outgoings: [nappies, sip])
            Check.equal(ledger.count, 6, "lines")
            // Newest first: the transfer three days ago, then the contribution, the expense,
            // the withdrawal, the deposit.
            Check.equal(ledger.map(\.id), ["m4", "m3", "c1", "e1", "m2", "m1"], "order")
            Check.isTrue(ledger.allSatisfy { $0.amount >= 0 }, "no line carries a negative amount")
        }

        Check.group("a line says which of the four things produced it") {
            let ledger = FundMath.ledger(movements: [deposit, withdrawal, outLeg, inLeg],
                                         outgoings: [nappies, sip])
            let byId = Dictionary(uniqueKeysWithValues: ledger.map { ($0.id, $0) })
            Check.equal(byId["m1"]?.title, "Deposit", "in")
            Check.equal(byId["m2"]?.title, "Withdrawal", "out")
            Check.equal(byId["m3"]?.title, "Transfer out", "the half that left")
            Check.equal(byId["m4"]?.title, "Transfer in", "the half that arrived")
            Check.isTrue(byId["m3"]?.transfer == true, "a transfer half is marked as one")
            Check.isTrue(byId["m1"]?.transfer == false, "an ordinary deposit is not")
            Check.isTrue(byId["e1"]?.origin == .expense, "an expense")
            Check.isTrue(byId["c1"]?.origin == .investment, "told apart from a contribution")
            Check.isTrue(byId["e1"]?.incoming == false, "and both are money out")
        }

        Check.group("two lines on the same day keep a settled order between renders") {
            let sameDay = FundMath.ledger(
                movements: [FundMath.Movement(id: "m7", date: day(-1), amount: 10),
                            FundMath.Movement(id: "m8", date: day(-1), amount: 20)],
                outgoings: []
            )
            Check.equal(sameDay.map(\.id), ["m8", "m7"], "order")
        }

        Check.group("the standing top-up comes due when its day passes unfed") {
            let march10 = cal.date(from: DateComponents(year: 2026, month: 3, day: 10))!
            let march1 = cal.date(from: DateComponents(year: 2026, month: 3, day: 1))!
            let february = cal.date(from: DateComponents(year: 2026, month: 2, day: 28))!
            Check.isTrue(FundMath.topUpDue(monthly: 5_000, depositDay: 1, lastDeposit: february,
                                           today: march10, calendar: cal), "due")
            Check.isTrue(!FundMath.topUpDue(monthly: 5_000, depositDay: 1, lastDeposit: march1,
                                            today: march10, calendar: cal), "already fed this month")
            Check.isTrue(!FundMath.topUpDue(monthly: 5_000, depositDay: 20, lastDeposit: february,
                                            today: march10, calendar: cal), "the day has not come")
            Check.isTrue(!FundMath.topUpDue(monthly: 0, depositDay: 1, lastDeposit: nil,
                                            today: march10, calendar: cal), "no plan, nothing due")
            Check.isTrue(FundMath.topUpDue(monthly: 5_000, depositDay: 1, lastDeposit: nil,
                                           today: march10, calendar: cal), "never fed at all")
        }
    }
}
