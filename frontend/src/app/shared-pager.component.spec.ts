import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PagerComponent } from './shared-pager.component';

describe('PagerComponent', () => {
  let fixture: ComponentFixture<PagerComponent>;
  let el: HTMLElement;

  const setup = (page: number, totalPages: number) => {
    fixture.componentRef.setInput('page', page);
    fixture.componentRef.setInput('totalPages', totalPages);
    fixture.detectChanges();
  };
  const buttons = () => Array.from(el.querySelectorAll('button')) as HTMLButtonElement[];

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [PagerComponent] });
    fixture = TestBed.createComponent(PagerComponent);
    el = fixture.nativeElement;
  });

  it('no se muestra con una sola página', () => {
    setup(0, 1);
    expect(el.querySelector('nav')).toBeNull();
  });

  it('muestra la página actual en base 1 y desactiva Anterior en la primera', () => {
    setup(0, 3);
    expect(el.textContent).toContain('Página 1 de 3');
    expect(buttons()[0].disabled).toBeTrue();
    expect(buttons()[1].disabled).toBeFalse();
  });

  it('desactiva Siguiente en la última página', () => {
    setup(2, 3);
    expect(buttons()[0].disabled).toBeFalse();
    expect(buttons()[1].disabled).toBeTrue();
  });

  it('emite la página anterior y la siguiente', () => {
    setup(1, 3);
    const emitted: number[] = [];
    fixture.componentInstance.pageChange.subscribe((p: number) => emitted.push(p));
    buttons()[0].click();
    buttons()[1].click();
    expect(emitted).toEqual([0, 2]);
  });
});
