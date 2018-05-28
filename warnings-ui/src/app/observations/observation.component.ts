import { Component, OnInit } from '@angular/core';

import {Observable} from 'rxjs/Observable';
import {Learned, LearningFailure, TracingResult} from "../../../../api-inference/API-tracer/src/tracing-results";
import 'rxjs/add/observable/of';
import 'rxjs/add/operator/mergeMap';
import 'rxjs/add/operator/map';
import {ActivatedRoute} from "@angular/router";
import {ObservationService} from "./observation.service";
import * as assert from "assert";
import {DebugPipe} from "../debug"
import {Observation} from "../../../../api-inference/API-tracer/src/observations";
import {DomSanitizer} from "@angular/platform-browser";

@Component({
  selector: 'file',
  templateUrl: './observation.component.html',
  styleUrls: [ './observation.component.css' ]
})
export class ObservationComponent {
  tracingResult: Observable<TracingResult>;

  ids: Map<any, any> = new Map();

  constructor(private route: ActivatedRoute,
              private obsService: ObservationService,
              private sanitizer: DomSanitizer) {
    this.tracingResult = this.route.params.mergeMap(p => obsService.getObservations(p.key));
  }

  prettyStackTrace(observation: Observation) {
    return this.sanitizer.bypassSecurityTrustHtml( "<pre>" + observation.stack + "</pre>");
  }

  getId(x): string {
    if(!this.ids.has(x))
      this.ids.set(x, this.ids.size.toString());
    return this.ids.get(x);
  }

  toggle(id) {

  }

}
