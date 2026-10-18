import React from 'react'
import { Navbar } from '../components/landing/components/Navbar'
import { Hero } from '../components/landing/sections/Hero'
import { ValueProposition } from '../components/landing/sections/ValueProposition'
import { HowItWorks } from '../components/landing/sections/HowItWorks'
import { Proof } from '../components/landing/sections/Proof'
import { CallToAction } from '../components/landing/sections/CallToAction'
import { Footer } from '../components/landing/components/Footer'

export default function LandingPage() {
  return (
    <div className="noise min-h-screen bg-canvas">
      <Navbar />
      <main>
        <Hero />
        <ValueProposition />
        <HowItWorks />
        <Proof />
        <CallToAction />
      </main>
      <Footer />
    </div>
  )
}