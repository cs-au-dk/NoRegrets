import {Component, Input, OnInit} from '@angular/core';
import {DomSanitizer} from "@angular/platform-browser";

import {AccessPath, WriteLabel} from "../../../../api-inference/API-tracer/src/paths";
import {
  AggregateClientDetails,
  AnalysisResults,
  ClientDetail,
  DifferentObservations,
  NoObservationInBase,
  NoObservationInPost,
  RelationFailure,
  TypeDiff
} from "../datastructures/diffs";
import {KeyValueObject, selectedRegressions} from "../utils";

@Component({
  selector : 'app-analysis-results',
  templateUrl : './analysisResults.component.html',
  styleUrls : [ './analysisResults.component.css' ]
})
export class AnalysisResultsComponent implements OnInit {
  @Input() analysisResults: KeyValueObject<AnalysisResults>;
  @Input() initVersionInfo: AnalysisResults;
  @Input() fseStylePaths: boolean;
  @Input() hidePrototypes: boolean;
  @Input() toolName: string;
  @Input() hideClientCoverage: boolean;

  pathStrs: string[] = [];
  paths: AccessPath[] = [];

  constructor(private sanitizer: DomSanitizer) {}

  ngOnInit() {
    this.analysisResults.value.regressionInfo.regressions.forEach(reg => {
      this.paths.push(reg[0]);
      this.refreshPaths();
    })
  }

  ngOnChanges() { this.refreshPaths(); }

  refreshPaths(): void {
    for (let i = 0; i < this.paths.length; i++) {
      this.pathStrs[i] =
          this.paths[i].toString(this.fseStylePaths, !this.fseStylePaths, this.hidePrototypes);
    }
  }

  sortedClientDetails(cds: ClientDetail[]): ClientDetail[] {
    return cds.sort((a, b) =>
                        a.packageAtVersion.localeCompare(b.packageAtVersion));
  }

  didFailInitially(clientPV: string): boolean {
    if (!this.initVersionInfo) {
      // This shouldn't happen because this method should be called after
      // setInit
      console.log("This shouldn't happen")
    }
    const pvToName = (pv: string) => pv.substring(0, pv.indexOf("@"));

    const clientResInit = this.initVersionInfo.clientDetails.find(
        clientDT => pvToName(clientDT.packageAtVersion) === pvToName(clientPV));
    if (clientResInit) {
      return !clientResInit.succeeded;
    }
    return false;
  }

  visualizeRegression(diff: TypeDiff) {
    return this.sanitizer.bypassSecurityTrustHtml(this._visualize(diff));
  }

  visualizeClient(diff: TypeDiff) {
    return this.sanitizer.bypassSecurityTrustHtml(this._visualizeClient(diff));
  }

  aggregateSucceedingClientDetails(cds: ClientDetail[]):
      AggregateClientDetails {
    const agg: AggregateClientDetails = {
      executionTime : 0,
      testTime : 0,
      modelSize : 0,
      compressedModelSize : 0,
      pathsTotal : 0,
      pathsCovered : 0,
      clientOrModelSizeBytes : 0
    };

    for (let cd of cds) {
      if (cd.succeeded) {
        agg.executionTime += cd.executionTimeMillis;
        agg.testTime += cd.testTimeMillis;
        agg.modelSize += cd.modelSize;
        agg.compressedModelSize += cd.compressedModelSize;
        agg.pathsTotal += cd.pathsTotal;
        agg.pathsCovered += cd.pathsCovered;
        agg.clientOrModelSizeBytes += cd.clientOrModelSizeBytes;
      }
    }
    return agg;
  }

  private cdRow(name: string, executionTimeMillis: number, modelSize: number,
                compressedModelSize: number, pathsTotal: number,
                pathsCovered: number, clientOrModelSizeBytes: number,
                clientOrModelSizeBytesWOEmptyModels: number): string {
    const style = "style =\"padding:0 15px 0 15px; font-weight: bold\"";
    return `
    <td ${style}>
            ${name}
    </tdj>
    <td ${style}>
            ${(executionTimeMillis / 1000).toFixed(3)}sec
    </td>
    <td ${style}>
            ${modelSize.toFixed(2)}
    </td>
    <td ${style}>
        ${compressedModelSize.toFixed(2)} (${
        modelSize > 0 ? (((modelSize - compressedModelSize) / modelSize) *
                         100).toFixed(3) +
                            "%"
                      : "NaN"})
    </td>
    <td ${style}>
        See aggregated data below
    </td>
    <td ${style}>
        ${(clientOrModelSizeBytes / 1000).toFixed(2)}kB (${
        (clientOrModelSizeBytesWOEmptyModels / 1000)
            .toFixed(2)}kB WO empty models)
    </td>`;
  }

  visualizeAverageClientDetail(cds: ClientDetail[]) {
    return this.sanitizer.bypassSecurityTrustHtml((() => {
      const agg = this.aggregateSucceedingClientDetails(cds);
      let l = cds.filter(cd => cd.succeeded).length;
      let lNonEmptySucceeded =
          cds.filter(cd => cd.modelSize > 0 && cd.succeeded).length;
      if (l == 0) {
        l = 1;
      }
      if (lNonEmptySucceeded == 0) {
        lNonEmptySucceeded = 1
      }
      const time = agg.executionTime;
      return this.cdRow("Average (succeeding)", time / l,
                        agg.modelSize / l, agg.compressedModelSize / l,
                        agg.pathsTotal / l, agg.pathsCovered / l,
                        agg.clientOrModelSizeBytes / l,
                        agg.clientOrModelSizeBytes / lNonEmptySucceeded);
    })());
  }

  visualizeTotalClientDetail(cds: ClientDetail[]) {
    return this.sanitizer.bypassSecurityTrustHtml((() => {
      const agg = this.aggregateSucceedingClientDetails(cds);
      const time = agg.executionTime;
      return this.cdRow("Total (succeeding)", time, agg.modelSize,
                        agg.compressedModelSize, agg.pathsTotal,
                        agg.pathsCovered, agg.clientOrModelSizeBytes,
                        agg.clientOrModelSizeBytes);
    })());
  }

  _visualizeClient(diff: TypeDiff) {
    if (diff instanceof RelationFailure) {
      let s = `<ul>`;
      for (let typeStr in diff.obs) {
        let observations = diff.obs[typeStr];
        for (let obs of observations) {
          s += `<li>${obs.pv.packageName}@${obs.pv.packageVersion}</li>`
        }
      }
      s += `</ul>`;
      return s;
    } else {
      return `<p>Missing implementation for client extraction of TypeDiff</p>`;
    }
  }

  _visualize(diff: TypeDiff) {
    if (diff instanceof NoObservationInBase) {
      return "<span title='" + diff.obs.map(x => x.stack).join("\n\n") +
             "'> No observation in base" +
             (diff.types ? "(" + diff.types.join(',') + ")" : "") + "</span>"
    } else if (diff instanceof NoObservationInPost) {
      return "<span title='" + diff.obs.map(x => x.stack).join("\n\n") +
             "'> No observation in post" +
             (diff.types ? "(" + diff.types.join(',') + ")" : "") + "</span>"
    } else if (diff instanceof RelationFailure) {
      let s = "Relation failure:<b>" +
              diff.relationFailure.replace('<:', '≮:').replace(':>', ':≯') + "</b>";
      //s    += `<br>`;
      //s    += `<ul><li>Affected types (hover for stack)</li>`;
      //s    += `<ul>`;
      //s    += `<li>`;
      //s    += Object.entries(diff.obs)
      //         .map(obs => `<span title="${
      //                  obs[1].map(x => x.stack).join("\n\n")}"><b>${
      //                  obs[0]}</b></span>`)
      //         .join("</li><li>");
      //s    += "</li>";
      //s    += "</ul>";
      //s    += "</ul>";
      return s;
    } else if (diff instanceof DifferentObservations) {
      let s = "<div>";
      for (let ttype in diff.types) {
        s += ttype + ":" + this._visualize(diff.types[ttype]) + "<br>";
      }
      s += "</div>";
      return s;
    }
  }

  // showStack(version, path): Observable<boolean> {
  //  return this.route.params.map(
  //      p => {return p.version == version &&
  //                   this.pathId(p.path) == this.pathId(path)});
  //}

  getStack(diff: TypeDiff): string {
    let ss = "";
    if (diff instanceof NoObservationInBase || diff instanceof
                                                   NoObservationInPost) {
      ss = diff.obs.map(x => x.stack).join("\n\n")
    } else if (diff instanceof DifferentObservations) {
      for (let ttype in diff.types) {
        ss += ttype + ":\n================\n" +
              this.prettyStackTrace(diff.types[ttype]) +
              "\n=================\n==================\n";
      }
    } else if (diff instanceof RelationFailure) {
      for (let ttype in diff.obs) {
        ss += ttype + ":\n================\n" +
              diff.obs[ttype].map(x => x.stack).join("\n\n") +
              "\n=================\n==================\n";
      }
    }
    return ss;
  }

  prettyStackTrace(diff: TypeDiff) { return this.getStack(diff); }

  pathId(path) { return path.toString().replace(/\(|\)/g, ""); }

  compressedFraction(cd: ClientDetail): string {
    return cd.modelSize > 0
               ? `${
                     (((cd.modelSize - cd.compressedModelSize) / cd.modelSize) *
                      100)
                         .toFixed(0)}%`
               : "NaN";
  }

  clientModelSizeString(): string {
    return this.toolName === 'NoRegretsPlus' ? 'Model size (bytes)' : 'Client size (bytes)';
  }

  getTime(cd: ClientDetail): number {
    return this.toolName === 'NoRegretsPlus' ? cd.testTimeMillis : cd.executionTimeMillis;
  }

  public selectedRegressions (regressions: [AccessPath, TypeDiff][]) {
    return selectedRegressions(regressions, this.toolName);
  }

}
