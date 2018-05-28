import {Component, OnInit} from '@angular/core';

import {Observable} from 'rxjs/Observable';
import {
  Learned,
  LearningFailure,
  TracingResult
} from "../../../../api-inference/API-tracer/src/tracing-results";
import 'rxjs/add/observable/of';
import 'rxjs/add/operator/mergeMap';
import 'rxjs/add/operator/map';
import {ActivatedRoute} from "@angular/router";
import * as assert from "assert";
import {DebugPipe} from "../debug"
import {DiffService} from "./diffs";
import {
  DifferentObservations, KnowledgeDiff, NoObservationInBase, NoObservationInPost,
  RelationFailure, TypeDiff
} from "../datastructures/diffs";
import {IDictionary} from "../datastructures/status";
import {_getEntries, verToNum} from "../utils";
import {DomSanitizer} from "@angular/platform-browser";

@Component({
  selector: 'file',
  templateUrl: './diffs.component.html',
  styleUrls: ['./diffs.component.css']
})
export class DiffComponent {
  diffResult: Observable<IDictionary<KnowledgeDiff>>;
  curKey: Observable<string>;

  constructor(private route: ActivatedRoute,
              private obsService: DiffService,
              private sanitizer: DomSanitizer) {
    this.diffResult = this.route.params.mergeMap(p => obsService.getDiff(p.key));
    this.curKey = this.route.params.map(p => p.key);
  }

  getEntries = _getEntries;

  cmpEntries(a, b) {
    return verToNum(a.key) - verToNum(b.key);
  }

  visualize(diff: TypeDiff) {
    return this.sanitizer.bypassSecurityTrustHtml(this._visualize(diff));
  }

  _visualize(diff: TypeDiff) {
    if (diff instanceof NoObservationInBase) {
      return "<span title='" + diff.obs.map(x => x.stack).join("\n\n") + "'> No observation in base" + (diff.types ? "(" + diff.types.join(',') + ")" : "") + "</span>"
    }
    else if (diff instanceof NoObservationInPost) {
      return "<span title='" + diff.obs.map(x => x.stack).join("\n\n") + "'> No observation in post" + (diff.types ? "(" + diff.types.join(',') + ")" : "") + "</span>"
    }
    else if (diff instanceof RelationFailure) {
      let s = "Relation failure:<b>" + diff.relationFailure + "</b>";
      s += `<br>`;
      s += `<ul><li>Affected types (hover for stack)</li>`;
      s += `<ul>`;
      s += `<li>`;
      s += Object.entries(diff.obs).map(obs => `<span title="${obs[1].map(x => x.stack).join("\n\n")}"><b>${obs[0]}</b></span>`).join("</li><li>");
      s += "</li>";
      s += "</ul>";
      s += "</ul>";
      return s;
    }
    else if (diff instanceof DifferentObservations) {
      let s = "<div>";
      for (let ttype in diff.types) {
        s += ttype + ":" + this._visualize(diff.types[ttype]) + "<br>";
      }
      s += "</div>";
      return s;
    }
  }

  showStack(version, path): Observable<boolean> {
    return this.route.params.map(p => {
      return p.version == version && this.pathId(p.path) == this.pathId(path)
    });
  }

  getStack(diff: TypeDiff): string {
    let ss = "";
    if (diff instanceof NoObservationInBase || diff instanceof NoObservationInPost) {
      ss = diff.obs.map(x => x.stack).join("\n\n")
    }
    else if (diff instanceof DifferentObservations) {
      for (let ttype in diff.types) {
        ss += ttype + ":\n================\n" + this.prettyStackTrace(diff.types[ttype]) + "\n=================\n==================\n";
      }
    }
    else if (diff instanceof RelationFailure) {
      for (let ttype in diff.obs) {
        ss += ttype + ":\n================\n" + diff.obs[ttype].map(x => x.stack).join("\n\n") + "\n=================\n==================\n";
      }
    }
    return ss;
  }

  prettyStackTrace(diff: TypeDiff) {
    return this.getStack(diff);
  }

  pathId(path) {
    return path.toString().replace(/\(|\)/g, "");
  }
}
