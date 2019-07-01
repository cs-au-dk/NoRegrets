import { Component, OnInit } from '@angular/core';

import {Observable} from 'rxjs';
import {TracingResult} from "../../../../api-inference/API-tracer/src/tracing-results";
import { mergeMap } from 'rxjs/operators';


import {ActivatedRoute} from "@angular/router";
import {ObservationService} from "./observation.service";
import {DebugPipe} from "../debug"
import {ReadObservation} from "../../../../api-inference/API-tracer/src/observations";
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
    this.tracingResult = this.route.params.pipe(mergeMap(p => obsService.getObservations(p.key)));
  }

  prettyStackTrace(observation: ReadObservation) {
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
