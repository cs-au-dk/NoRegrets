import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { BenchmarkCompareViewComponent } from './benchmark-compare-view.component';

describe('BenchmarkCompareViewComponent', () => {
  let component: BenchmarkCompareViewComponent;
  let fixture: ComponentFixture<BenchmarkCompareViewComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ BenchmarkCompareViewComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(BenchmarkCompareViewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
