import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AnalysisResultsComponent } from './analysisResults.component';

describe('AnalysisResultsComponent', () => {
  let component: AnalysisResultsComponent;
  let fixture: ComponentFixture<AnalysisResultsComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AnalysisResultsComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AnalysisResultsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
