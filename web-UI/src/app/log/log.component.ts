import { Component, OnInit } from '@angular/core';

import {LogService} from "./log.service";
import {Observable} from 'rxjs';
import {TracingResult} from "../../../../api-inference/API-tracer/src/tracing-results";

import {ActivatedRoute} from "@angular/router";
import {mergeMap} from "rxjs/operators";

@Component({
  selector: 'file',
  templateUrl: './log.component.html',
  styleUrls: [ './log.component.css' ]
})
export class LogComponent {
  observations: Observable<TracingResult>;
  log: Observable<string>;

  constructor(private route: ActivatedRoute,
              private fileService: LogService) {
    this.log = this.route.params.pipe(mergeMap(p =>  fileService.getLearnedLog(p.key)));
    this.log.subscribe(text => {
      let ansiup = new (window as any).AnsiUp;
      let html = ansiup.ansi_to_html(text);
      let cdiv = document.getElementById("console");
      cdiv.innerHTML = html;
    });
  }
}
