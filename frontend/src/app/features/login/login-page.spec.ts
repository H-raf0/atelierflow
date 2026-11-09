import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { LoginPage } from './login-page';

describe('LoginPage', () => {
  let fixture: ComponentFixture<LoginPage>;
  let element: HTMLElement;
  const auth = { login: vi.fn() };

  beforeEach(async () => {
    auth.login.mockReset();
    await TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    }).compileComponents();
    fixture = TestBed.createComponent(LoginPage);
    fixture.componentRef.setInput('returnUrl', '/equipment');
    element = fixture.nativeElement;
    await fixture.whenStable();
  });

  const type = (testId: string, value: string) => {
    const input = element.querySelector<HTMLInputElement>(`[data-testid="${testId}"]`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };
  const submit = async () => {
    element.querySelector<HTMLButtonElement>('[data-testid="login"]')!.click();
    await fixture.whenStable();
  };

  it('does not call the API while the form is incomplete', async () => {
    await submit();

    expect(auth.login).not.toHaveBeenCalled();
    expect(element.textContent).toContain("L'identifiant est obligatoire");
  });

  it('signs in and goes back to the requested page', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    auth.login.mockResolvedValue(undefined);
    type('username', 'chef');
    type('password', 'secret');

    await submit();

    expect(auth.login).toHaveBeenCalledWith('chef', 'secret');
    expect(navigate).toHaveBeenCalledWith('/equipment');
  });

  it('shows the reason of a failed sign-in', async () => {
    auth.login.mockRejectedValue(new HttpErrorResponse({ status: 401, error: { code: 'INVALID_CREDENTIALS' } }));
    type('username', 'chef');
    type('password', 'wrong');

    await submit();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Identifiant ou mot de passe incorrect');
  });

  it('fills a demo account in one click', async () => {
    const buttons = Array.from(element.querySelectorAll<HTMLButtonElement>('.demo button'));
    buttons.find((b) => b.textContent?.includes('Technicien'))!.click();
    await fixture.whenStable();

    expect(element.querySelector<HTMLInputElement>('[data-testid="username"]')!.value).toBe('technicien');
  });
});
