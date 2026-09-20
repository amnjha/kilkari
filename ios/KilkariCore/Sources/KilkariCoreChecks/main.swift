import Foundation

print("KilkariCore checks")
GrowthStandardsChecks.run()
CoreChecks.runUnits()
CoreChecks.runReturns()
CoreChecks.runSchedules()
CoreChecks.runPaperwork()
Check.report()
