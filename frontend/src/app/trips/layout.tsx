import { SiteHeader } from "@/components/site-header";

export default function TripsLayout({ children }: LayoutProps<"/trips">) {
  return (
    <>
      <SiteHeader />
      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">{children}</main>
    </>
  );
}
