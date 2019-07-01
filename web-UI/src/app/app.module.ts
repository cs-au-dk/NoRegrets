import {HttpClientModule, HttpClient} from "@angular/common/http";
import {NgModule} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MatCheckboxModule, MatExpansionModule, MatCardModule} from '@angular/material';
import {BrowserModule} from '@angular/platform-browser';
import {BrowserAnimationsModule} from '@angular/platform-browser/animations';
import {NgbModule} from '@ng-bootstrap/ng-bootstrap';

import {AnalysisDataService} from "./analysisData.service";
import {
  AnalysisResultsComponent
} from './analysisResults/analysisResults.component';
import {
  AnalysisResultsOverviewComponent
} from "./analysisResultsOverview/analysisResultsOverview.component";
import {AppRoutingModule} from './app-routing.module';
import {AppComponent} from './app.component';
import {DebugPipe} from "./debug";
import {LogComponent} from './log/log.component';
import {LogService} from "./log/log.service";
import {ObservationComponent} from "./observations/observation.component";
import {ObservationService} from "./observations/observation.service";
import {StatusComponent} from './status/status.component';
import { DocsComponent } from './docs/docs.component';
import { MarkdownModule } from "ngx-markdown";
import { BenchmarkCompareViewComponent } from './benchmark-compare-view/benchmark-compare-view.component';

@NgModule({
  imports : [
    BrowserModule, FormsModule, HttpClientModule, AppRoutingModule,
    NgbModule.forRoot(), BrowserAnimationsModule, MatExpansionModule,
    MatCheckboxModule, MatCardModule, MarkdownModule.forRoot( { loader: HttpClient })
  ],
  declarations : [
    AppComponent, LogComponent, StatusComponent, ObservationComponent,
    DebugPipe, AnalysisResultsOverviewComponent, AnalysisResultsComponent, DocsComponent, BenchmarkCompareViewComponent
  ],
  providers : [ LogService, ObservationService, AnalysisDataService ],
  bootstrap : [ AppComponent ]
})
export class AppModule {
}
