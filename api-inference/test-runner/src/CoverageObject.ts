// case class Lines(covered: Int, pct: Double, skipped: Int, total: Int)
// case class Statements(covered: Int, pct: Double, skipped: Int, total: Int)

interface Lines {
  covered: number, pct: number, skipped: number, total: number
}

interface Statements {
  covered: number, pct: number, skipped: number, total: number
}

export class CoverageObject {
  constructor(public statementCoverage?: {[index: string]: Statements},
              public lineCoverage?: {[index: string]: Lines},
              public linesTotal?: number) {
    this.statementCoverage =
        this.statementCoverage ? this.statementCoverage : {};
    this.lineCoverage = this.lineCoverage ? this.lineCoverage : {};
    this.linesTotal = this.linesTotal ? this.linesTotal : 0;
  }

  static fromJson(i): CoverageObject {
    return new CoverageObject(i.statementCoverage, i.lineCoverage,
                              i.linesTotal);
  }

  public addStatementCoverage(index: string, statements: Statements) {
    this.statementCoverage[index] = statements;
  }

  public hasStatementCoverage(): boolean {
    return Object.keys(this.statementCoverage).length > 0;
  }

  public hasLineCoverage(): boolean {
    return Object.keys(this.lineCoverage).length > 0;
  }

  public addLineCoverage(index: string, lines: Lines) {
    this.lineCoverage[index] = lines;
  }

  public addlinesTotal(lines: number) { this.linesTotal += lines; }
}
