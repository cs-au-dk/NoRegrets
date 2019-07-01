import {NgModule} from '@angular/core';
import {RouterModule, Routes} from '@angular/router';

import {AnalysisResultsOverviewComponent} from "./analysisResultsOverview/analysisResultsOverview.component";
import {LogComponent} from "./log/log.component";
import {ObservationComponent} from "./observations/observation.component";
import {StatusComponent} from "./status/status.component";
import {DocsComponent} from "./docs/docs.component";
import {BenchmarkCompareViewComponent} from "./benchmark-compare-view/benchmark-compare-view.component";

const routes: Routes = [
  {path : '', redirectTo : '/status', pathMatch : 'full'},
  {path : 'docs', component : DocsComponent},
  {path : 'log/:key', component : LogComponent},
  {path : 'observations/:key', component : ObservationComponent},
  {path : 'status', component : StatusComponent},
  {path : 'diffs/:key', component : AnalysisResultsOverviewComponent},
  {path : 'compare/:firstKey/:secondKey', component: BenchmarkCompareViewComponent}
];

@NgModule({
  imports : [ RouterModule.forRoot(routes, {useHash : true}) ],
  exports : [ RouterModule ]
})
export class AppRoutingModule {
}
