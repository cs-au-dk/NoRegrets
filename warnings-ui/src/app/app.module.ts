import { NgModule }      from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { FormsModule }   from '@angular/forms';
import { HttpModule }    from '@angular/http';

import { AppRoutingModule } from './app-routing.module';

import { AppComponent }         from './app.component';
import { LogComponent }  from './log/log.component';
import {LogService} from "./log/log.service";
import { ClientDetailsComponent } from './client-details/client-details.component';
import { StatusComponent } from './status/status.component';
import {BenchmarkStatusService} from "./benchmark-status.service";
import {ObservationComponent} from "./observations/observation.component";
import {ObservationService} from "./observations/observation.service";
import {DebugPipe} from "./debug";
import {DiffComponent} from "./diffs/diffs.component";
import {DiffService} from "./diffs/diffs";
import {NgbModule} from '@ng-bootstrap/ng-bootstrap';

@NgModule({
  imports: [
    BrowserModule,
    FormsModule,
    HttpModule,
    AppRoutingModule,
    NgbModule.forRoot()
  ],
  declarations: [
    AppComponent,
    LogComponent,
    ClientDetailsComponent,
    StatusComponent,
    ObservationComponent,
    DebugPipe,
    DiffComponent
  ],
  providers: [ LogService, ObservationService, BenchmarkStatusService, DiffService ],
  bootstrap: [ AppComponent ]
})
export class AppModule { }
